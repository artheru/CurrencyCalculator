package artheru.com.exchanger;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.goebl.david.Webb;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class Calculator extends AppCompatActivity {

    private Handler mHandler;
    private SharedPreferences rates;
    private Map<String, Float> map;
    private ArrayList<String> choose;
    private Map<String, EditText> editMap;
    private LinearLayout ratesView;
    private String timestamp;

    public void sync(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String exchanges = Webb.create().get("http://www.x-rates.com/table/?from=CNY&amount=1").asString().getBody();
                    HtmlCleaner cleaner = new HtmlCleaner();
                    TagNode root = cleaner.clean(exchanges);

                    SharedPreferences.Editor editor = rates.edit();
                    map.clear();
                    map.put("CNY", (float) 1.0);
                    ArrayList<String> tmp = new ArrayList<>();
                    tmp.add("CNY:1");

                    TagNode tbody= (TagNode) root.evaluateXPath("//*[@id=\"content\"]/div[1]/div/div[1]/div[1]/table[2]/tbody")[0];
                    TagNode[] trs=tbody.getElementsByName("tr",false);
                    for (TagNode tr: trs){
                        String currency = ((TagNode)tr.evaluateXPath("/td[1]")[0]).getText().toString();
                        float rate = Float.parseFloat(((TagNode)tr.evaluateXPath("/td[2]")[0]).getText().toString());
                        map.put(currency, rate);
                        tmp.add(currency + ':' + rate);
                    }

                    timestamp= ((TagNode)root.evaluateXPath("//*[@id=\"content\"]/div[1]/div/div[1]/div[1]/span[2]")[0]).getText().toString();
                    mHandler.sendEmptyMessage(0);
                    editor.putString("time", timestamp);
                    editor.putString("rates",TextUtils.join(",",tmp));
                    editor.commit();
                    Log.d("Sync","Good");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);
        Button btn = (Button) findViewById(R.id.button);
        assert btn != null;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sync();
            }
        });
        ratesView = (LinearLayout) findViewById(R.id.rates);

        Button btn2= (Button) findViewById(R.id.button2);
        assert btn2 != null;
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int sz=map.size();
                final CharSequence[] css=new CharSequence[sz];
                boolean[] bss=new boolean[sz];
                int i=0;
                for (String key:map.keySet()){
                    css[i]=key;
                    bss[i]=choose.contains(key);
                    ++i;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(Calculator.this);
                builder.setTitle("Choose Currencies to calculate")
                        .setMultiChoiceItems(css, bss, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                if (isChecked && !choose.contains(css[which]))
                                    choose.add(String.valueOf(css[which]));
                                else if (!isChecked && choose.contains(css[which]))
                                    choose.remove(String.valueOf(css[which]));
                                SharedPreferences.Editor editor = rates.edit();
                                editor.putString("choose",TextUtils.join(",",choose));
                                editor.apply();
                            }
                        })
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                updateView();
                            }
                        })
                .show();
            }
        });

        mHandler=new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                updateView();
            }
        };

        rates=getSharedPreferences("cal", Context.MODE_PRIVATE);
        map = new HashMap<String, Float>();
        timestamp = rates.getString("time","Never");
        String s = rates.getString("rates","CNY:1");
        String[] pairs = s.split(",");
        for (int i=0;i<pairs.length;i++) {
            String pair = pairs[i];
            String[] keyValue = pair.split(":");
            map.put(keyValue[0], Float.parseFloat(keyValue[1]));
        }
        s = rates.getString("choose","CNY");
        choose = new ArrayList<String>(Arrays.asList(s.split(",")));


        updateView();
    }

    private void updateView() {
        ratesView.removeAllViews();
        editMap = new HashMap<>();
        ((TextView)findViewById(R.id.textView)).setText(timestamp);
        for (final Map.Entry<String, Float> entry : map.entrySet())
        {
            if (!choose.contains(entry.getKey()))
                continue;
            LinearLayout layout = new LinearLayout(this);
            getLayoutInflater().inflate(R.layout.rate,layout);

            TextView titleView = (TextView) layout.findViewById(R.id.textView2);
            titleView.setText(entry.getKey());

            final EditText editText= (EditText) layout.findViewById(R.id.editText);
            editText.setText(entry.getValue().toString());
            editMap.put(entry.getKey(), editText);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!editText.hasFocus()) return;
                    try {
                        Float value = Float.parseFloat(String.valueOf(s))/entry.getValue();
                        for (Map.Entry<String, EditText> entry2 : editMap.entrySet()) {
                            if (!entry2.getKey().equals(entry.getKey()))
                                entry2.getValue().setText(String.valueOf(value * map.get(entry2.getKey())));
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            ratesView.addView(layout);
        }
    }
}
