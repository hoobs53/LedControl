package iot.examples.ledcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import iot.examples.ledcontrol.R;

public class MainActivity extends AppCompatActivity {

    /* BEGIN widgets */
    SeekBar redSeekBar, greenSeekBar, blueSeekBar;
    TextView statusText;

    /* END widgets */
    /* BEGIN colors */
    int ledActiveColorA; ///< Active color Alpha components
    int ledActiveColorR; ///< Active color Red components
    int ledActiveColorG; ///< Active color Green components
    int ledActiveColorB; ///< Active color Blue components

    int ledActiveColor;  ///< Active color in Int ARGB format

    int ledOffColor;       ///< 'LED-is-off' color in Int ARGB format
    Vector<Integer> ledOffColorVec; ///< 'LED-is-off' color in Int ARGB format

    Integer[][][] ledDisplayModel = new Integer[8][8][3]; ///< LED display data model
    Integer[][][] currentLedDisplayModel = new Integer[8][8][3];
    /* BEGIN colors */

    /* BEGIN request */
    String url = "http://192.168.1.104/cgi-bin/led_display.py";  ///< Default IoT server script URL
    private RequestQueue queue; ///< HTTP requests queue
    Map<String, String>  paramsClear = new HashMap<String, String>(); ///< HTTP POST data: clear display command
    /* END request */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* BEGIN Color data initialization */
        ledOffColor = argbToInt(100,0,0,0);
        ledOffColorVec = intToRgb(ledOffColor);

        ledActiveColor = ledOffColor;

        ledActiveColorR = 0x00;
        ledActiveColorG = 0x00;
        ledActiveColorB = 0x00;

        clearDisplayModel();
        /* END Color data initialization */

        /* BEGIN widgets initialization */
        redSeekBar = (SeekBar)findViewById(R.id.seekBarR);
        redSeekBar.setMax(248);
        redSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {/* Auto-generated method stub */ }
            public void onStopTrackingTouch(SeekBar seekBar) {
                ledActiveColor = seekBarUpdate('R', progressChangedValue);
            }
        });

        greenSeekBar = (SeekBar)findViewById(R.id.seekBarG);
        greenSeekBar.setMax(248);
        greenSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {/* Auto-generated method stub */ }
            public void onStopTrackingTouch(SeekBar seekBar) {
                ledActiveColor = seekBarUpdate('G', progressChangedValue);
            }
        });

        blueSeekBar = (SeekBar)findViewById(R.id.seekBarB);
        blueSeekBar.setMax(248);
        blueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChangedValue = 0;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
            }
            public void onStartTrackingTouch(SeekBar seekBar) {/* Auto-generated method stub */ }
            public void onStopTrackingTouch(SeekBar seekBar) {
                ledActiveColor = seekBarUpdate('B', progressChangedValue);
            }
        });

        statusText = findViewById(R.id.LEDStatus);
        statusText.setTextColor(argbToInt(100,255,0,0));
        /* END widgets initialization */

        /* BEGIN 'Volley' request queue initialization */

        queue = Volley.newRequestQueue(this);

        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                // "LEDij" : "[i,j,r,g,b]"
                String data ="["+Integer.toString(i)+","+Integer.toString(j)+",0,0,0]";
                paramsClear.put(ledIndexToTag(i, j), data);
                currentLedDisplayModel[i][j][0] = 0;
                currentLedDisplayModel[i][j][1] = 0;
                currentLedDisplayModel[i][j][2] = 0;

            }
        }
        initAllLed();
        getLeds();

        /* END 'Volley' request queue initialization */

    }


    public int argbToInt(int _a, int _r, int _g, int _b){
        return  (_a & 0xff) << 24 | (_r & 0xff) << 16 | (_g & 0xff) << 8 | (_b & 0xff);
    }

    public Vector<Integer> intToRgb(int argb) {
        int _r = (argb >> 16) & 0xff;
        int _g = (argb >> 8) & 0xff;
        int _b = argb & 0xff;
        Vector<Integer> rgb = new Vector<>(3);
        rgb.add(0,_r);
        rgb.add(1,_g);
        rgb.add(2,_b);
        return rgb;
    }

    Vector<Integer> ledTagToIndex(String tag) {
        // Tag: 'LEDxy"
        Vector<Integer> vec = new Vector<>(2);
        vec.add(0, Character.getNumericValue(tag.charAt(3)));
        vec.add(1, Character.getNumericValue(tag.charAt(4)));
        return vec;
    }


    String ledIndexToTag(int x, int y) {
        return "LED" + Integer.toString(x) + Integer.toString(y);
    }


    String ledIndexToJsonData(int x, int y) {
        String _x = Integer.toString(x);
        String _y = Integer.toString(y);
        String _r = Integer.toString(ledDisplayModel[x][y][0]);
        String _g = Integer.toString(ledDisplayModel[x][y][1]);
        String _b = Integer.toString(ledDisplayModel[x][y][2]);
        return "["+_x+","+_y+","+_r+","+_g+","+_b+"]";
    }

    boolean ledColorNotNull(int x, int y) {
        return !((ledDisplayModel[x][y][0]==null)||(ledDisplayModel[x][y][1]==null)||(ledDisplayModel[x][y][2]==null));
    }

    public void clearDisplayModel() {
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ledDisplayModel[i][j][0] = null;
                ledDisplayModel[i][j][1] = null;
                ledDisplayModel[i][j][2] = null;
            }
        }
    }

    int seekBarUpdate(char color, int value) {
        switch(color) {
            case 'R': ledActiveColorR = value; break;
            case 'G': ledActiveColorG = value; break;
            case 'B': ledActiveColorB = value; break;
            default: /* Do nothing */ break;
        }
        ledActiveColorA = 100;
        return argbToInt(ledActiveColorA,  ledActiveColorR, ledActiveColorG, ledActiveColorB);
    }

    public void changeLedIndicatorColor(View v) {
        // Set active color as background
        v.setBackgroundColor(ledActiveColor);
        // Find element x-y position
        String tag = (String)v.getTag();
        Vector<Integer> index = ledTagToIndex(tag);
        int x = (int)index.get(0);
        int y = (int)index.get(1);
        // Update LED display data model
        ledDisplayModel[x][y][0] = ledActiveColorR;
        ledDisplayModel[x][y][1] = ledActiveColorG;
        ledDisplayModel[x][y][2] = ledActiveColorB;
        setStatusText(checkIfChanged());
    }
    public void setStatusText(boolean condition){
        if(condition){
            statusText.setText("UNSAVED CHANGES!");
        }
        else {
            statusText.setText("");
        }
    }
    public boolean checkIfChanged(){
        int r;
        int g;
        int b;
        int r2;
        int g2;
        int b2;
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                r = currentLedDisplayModel[i][j][0];
                g = currentLedDisplayModel[i][j][1];
                b = currentLedDisplayModel[i][j][2];
                if (ledDisplayModel[i][j][0]!=null) {
                    r2 = ledDisplayModel[i][j][0];
                    g2 = ledDisplayModel[i][j][1];
                    b2 = ledDisplayModel[i][j][2];
                }
                else{
                    r2 = 0;
                    g2 = 0;
                    b2 = 0;
                }
                int color = argbToInt(100, r, g, b);
                int color2 = argbToInt(100, r2, g2, b2);
                if(color2 != color){
                    return true;
                }
            }
        }
        return false;
    }

    public void clearAllLed(View v) {
        // Clear LED display GUI
        TableLayout tb = (TableLayout)findViewById(R.id.ledTable);
        View ledInd;
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ledInd = tb.findViewWithTag(ledIndexToTag(i, j));
                ledInd.setBackgroundColor(ledOffColor);
            }
        }

        // Clear LED display data model
        clearDisplayModel();

        // Clear physical LED display
        sendClearRequest();
    }
    public void initAllLed(){
        TableLayout tb = (TableLayout)findViewById(R.id.ledTable);
        View ledInd;
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ledInd = tb.findViewWithTag(ledIndexToTag(i, j));
                ledInd.setBackgroundColor(ledOffColor);
            }
        }

        // Clear LED display data model
        clearDisplayModel();
    }
    public void loadLedColor(){
        TableLayout tb = (TableLayout)findViewById(R.id.ledTable);
        View ledInd;
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ledInd = tb.findViewWithTag(ledIndexToTag(i, j));
                int r = currentLedDisplayModel[i][j][0];
                int g = currentLedDisplayModel[i][j][1];
                int b = currentLedDisplayModel[i][j][2];
                ledDisplayModel[i][j][0] = r;
                ledDisplayModel[i][j][1] = g;
                ledDisplayModel[i][j][2] = b;
                int color = argbToInt(100,r,g,b);
                ledInd.setBackgroundColor(color);
            }
        }
    }

    public Map<String, String>  getDisplayControlParams() {
        String led;
        String position_color_data;
        Map<String, String>  params = new HashMap<String, String>();
        for(int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if(ledColorNotNull(i,j)) {
                    led = ledIndexToTag(i, j);
                    position_color_data = ledIndexToJsonData(i, j);
                    params.put(led, position_color_data);
                    int r = ledDisplayModel[i][j][0];
                    int g = ledDisplayModel[i][j][1];
                    int b = ledDisplayModel[i][j][2];
                    currentLedDisplayModel[i][j][0] = r;
                    currentLedDisplayModel[i][j][1] = g;
                    currentLedDisplayModel[i][j][2] = b;
                }
            }
        }
        setStatusText(false);
        return params;
    }
    private void getLeds(){
        String url2 = "http://192.168.1.104/cgi-bin/get_pixels.py";
        RequestQueue queue2 = Volley.newRequestQueue(this);
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, url2, null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                try {
                    int counter = 0;
                    for (int i = 0; i < 8; i++) {
                        for (int j = 0; j < 8; j++) {
                            String object = response.getString(counter).replace("[", "").replace("]", "");
                            String [] numArray = object.split(",");
                            counter++;
                            currentLedDisplayModel[i][j][0] = Integer.parseInt(numArray[0]);
                            currentLedDisplayModel[i][j][1] = Integer.parseInt(numArray[1]);
                            currentLedDisplayModel[i][j][2] = Integer.parseInt(numArray[2]);
                        }
                    }
                    loadLedColor();
                } catch (JSONException e) {
                    //TODO: handle error
                }
            }

        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //TODO: handle error
            }
        }
        );
        queue2.add(jsonArrayRequest);
    }


    public void sendControlRequest(View v)
    {
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        if(!response.equals("ACK")) {
                            Log.d("Response", "\n" + response);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String msg = error.getMessage();
                        if(msg != null)
                            Log.d("Error.Response", msg);
                        else {
                            // TODO: error type specific code
                        }
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                return getDisplayControlParams();
            }
        };
        postRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(postRequest);
    }


    void sendClearRequest()
    {
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        Log.d("Response", response);
                        // TODO: check if ACK is valid
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String msg = error.getMessage();
                        if(msg != null)
                            Log.d("Error.Response", msg);
                        else {
                            // TODO: error type specific code
                        }
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                setStatusText(false);
                return paramsClear;
            }
        };
        postRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(postRequest);
    }
}
