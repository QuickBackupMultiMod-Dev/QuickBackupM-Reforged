package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class PbsConfig {
    public boolean enabled = false;
    public Integer maxAmount = 10;
    public String maxLifeTime = "0s";
    public int last = -1;
    public int hour = 0;
    public int day = 0;
    public int week = 0;
    public int month = 1;
    public int year = 0;
}
