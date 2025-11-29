package com.llw.newmapdemo;

import android.content.Context;

public class WeatherController {

    public interface OnWeatherResultListener {
        void onLoading(boolean show);
        void onSuccess(WeatherData data);
        void onError(String msg);
    }

    private final WeatherService service;

    public WeatherController(Context context) {
        this.service = new WeatherService(context);
    }

    public void query(String city, String date, OnWeatherResultListener listener) {
        listener.onLoading(true);
        service.queryWeather(city, date, new WeatherService.WeatherCallback() {
            @Override
            public void onSuccess(WeatherData weatherData) {
                listener.onLoading(false);
                listener.onSuccess(weatherData);
            }

            @Override
            public void onFailure(String errorMessage) {
                listener.onLoading(false);
                listener.onError(errorMessage);
            }
        });
    }
}