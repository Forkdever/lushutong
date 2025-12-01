package com.example.plan;

import com.google.gson.annotations.SerializedName;
import java.util.Date;
import java.util.List;
import com.example.plan.Day;
public class Content {
    private String destination;
    @SerializedName("start_date")
    private Date startDate;
    @SerializedName("end_date")
    private Date endDate;
    private List<Day> days;
    private Transport transport;
    private String notes;

    public Content(String destination, Date startDate, Date endDate, List<Day> days,
                   Transport transport, String notes) {
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.transport = transport;
        this.notes = notes;
    }

    // Getter/Setter方法
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
    public List<Day> getDays() { return days; }
    public void setDays(List<Day> days) { this.days = days; }
    public Transport getTransport() { return transport; }
    public void setTransport(Transport transport) { this.transport = transport; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}