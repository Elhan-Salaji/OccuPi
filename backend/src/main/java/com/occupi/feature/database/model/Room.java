package com.occupi.feature.database.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    private String id;
    private String name;
    private String building;
    private int floor;
    private int currentOccupancy;
    private int maxCapacity;
    private String status;
    private String lastUpdate;
}