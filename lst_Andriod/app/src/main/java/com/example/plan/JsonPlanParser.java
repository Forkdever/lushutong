
package com.example.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import org.bson.types.ObjectId;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * JSON解析工具类，用于将JSON字符串转换为TravelPlan对象
 */
public class JsonPlanParser {
    private Gson gson;



    // 日期反序列化器
    private class DateDeserializer implements JsonDeserializer<Date> {
        private final SimpleDateFormat[] dateFormats = {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        };

        @Override
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            String dateStr = json.getAsString();

            // 处理ISODate格式
            if (dateStr.contains("ISODate(")) {
                dateStr = dateStr.replace("ISODate(", "").replace(")", "").replace("\"", "");
            }

            for (SimpleDateFormat format : dateFormats) {
                try {
                    return format.parse(dateStr);
                } catch (ParseException e) {
                    continue;
                }
            }
            throw new JsonParseException("无法解析日期: " + dateStr);
        }
    }

}
