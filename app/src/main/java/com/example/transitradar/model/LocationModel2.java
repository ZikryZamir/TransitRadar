package com.example.transitradar.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

//getter and setter
public class LocationModel2 implements Serializable {
    private static final long serialVersionUID = 1L;
    @SerializedName("LineID")
    private String lineId;

    @SerializedName("Line")
    private String line;

    @SerializedName("Line_Colour")
    private String lineColour;

    @SerializedName("Remark")
    private String remark;

    @SerializedName("Status")
    private String status;

    @SerializedName("Status_Colour")
    private String statusColour;

    // Getters
    public String getLineId() {
        return lineId;
    }

    public String getLine() {
        return line;
    }

    public String getLineColour() {
        return lineColour;
    }

    public String getRemark() {
        return remark;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusColour() {
        return statusColour;
    }

    // Constructor
    public LocationModel2(String lineId, String line, String lineColour, String remark, String status, String statusColour) {
        this.lineId = lineId;
        this.line = line;
        this.lineColour = lineColour;
        this.remark = remark;
        this.status = status;
        this.statusColour = statusColour;
    }
}
