package com.example.tc.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tc.coolweather.db.City;
import com.example.tc.coolweather.db.County;
import com.example.tc.coolweather.db.Province;
import com.example.tc.coolweather.gson.Weather;
import com.example.tc.coolweather.util.HttpUtil;
import com.example.tc.coolweather.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();
    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    private Province selectedProvince;
    private City selectedCity;
    //    当前选择的级别
    private int currentLevel;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        /*
         * 【LayoutInflater】其实是在res/layout/下找到xml布局文件，并且将其实例化，
         * 对于一个没有被载入或者想要动态载入的界面，都需要使用LayoutInflater.inflate()来载入；
         * */
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        //载入listView
        listView.setAdapter(adapter);
        return view;
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //对列表设置监听事件
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(currentLevel == LEVEL_PROVINCE){
                    //记住选中的省份
                    selectedProvince = provinceList.get(position);
                    //显示出省份对应下city的界面
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    //记住选中的City
                    selectedCity = cityList.get(position);
                    //切换到相应的county界面
                    queryCounties();
                }else if(currentLevel == LEVEL_COUNTY){
                    String weatherId = countyList.get(position).getWeatherId();
                    if(getActivity() instanceof MainActivity){
                        Intent intent = new Intent(getActivity(),WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if(getActivity() instanceof WeatherActivity){
                        WeatherActivity activity = (WeatherActivity)getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }
                }
            }
        });
        //为返回按钮注册监听事件
        backButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                //若在county切换到City
                if(currentLevel == LEVEL_COUNTY){
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    //若在City切换到province
                    queryProvinces();
                }
            }
        });
        //初始状态下显示province
        queryProvinces();
    }
    private void queryProvinces(){
        //设置标题栏
        titleText.setText("中国");
        //隐藏返回按钮
        backButton.setVisibility(View.GONE);
        //在数据库中查询所有省份
        provinceList = DataSupport.findAll(Province.class);
        if(provinceList.size()>0){
            dataList.clear();
            for(Province province:provinceList){
                dataList.add(province.getProvinceName());
            }
            //更新适配器中的内容，变为省份数据
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }else{
            //从服务器中查询
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }
    /*查询对应省的City数据，优先从数据库查，若没有，则去服务器查询
     * */
    private void queryCities(){
        //设置标题栏
        titleText.setText(selectedProvince.getProvinceName());
        //设置返回按钮可见
        backButton.setVisibility(View.VISIBLE);
        //在数据库中查询对应的City数据
        cityList = DataSupport.where("provinceid= ?",
                String.valueOf(selectedProvince.getId())).find(City.class);
        if(cityList.size()>0){
            dataList.clear();
            for(City city : cityList){
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }
        else{

            String address = "http://guolin.tech/api/china/"+selectedProvince.getProvinceCode();
            queryFromServer(address,"city");
        }
    }
    /*查询选中的市内的所有县，优先从数据库查，若没有则去服务器查询
     * */
    private void queryCounties(){
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityid= ?",String.valueOf(selectedCity.getId())).find(County.class);
        if(countyList.size()>0){
            dataList.clear();
            for(County county:countyList){
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        }else{
            String address = "http://guolin.tech/api/china/"+
                    selectedProvince.getProvinceCode()+"/"+selectedCity.getCityCode();
            queryFromServer(address,"county");
        }
    }
    /*根据传入的地址和类型从服务器上获取数据
     * */
    private void queryFromServer(String address,final String type){
        //未查出之前显示出进度条框
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //通过runOnUiThread回到主线程处理逻辑
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if(type.equals("province")){
                    result = Utility.handleProvinceResponse(responseText);
                }else if(type.equals("city")){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if(type.equals("county")){
                    result = Utility.handleCountyResponse(responseText,selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if(type.equals("province")){
                                queryProvinces();
                            }else if(type.equals("city")){
                                queryCities();
                            }else if(type.equals("county")){
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }
    private void showProgressDialog(){
        if(progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载…");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }
    //关闭进度框
    private void closeProgressDialog(){
        if(progressDialog != null){
            progressDialog.dismiss();
        }
    }
}