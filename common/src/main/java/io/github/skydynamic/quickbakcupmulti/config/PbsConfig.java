package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class PbsConfig {
    private boolean enabled = false;
    private Integer maxAmount = 10;
    private String maxLifeTime = "0s";
    private int last = -1;
    private int hour = 0;
    private int day = 0;
    private int week = 0;
    private int month = 1;
    private int year = 0;
}
