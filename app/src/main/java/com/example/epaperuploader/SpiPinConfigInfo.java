package com.example.epaperuploader;

//sck=13, din=14, cs=15, busy=25, rst=26, dc=27
public class SpiPinConfigInfo {
    public String config_name;
    private int din;
    private int sck;
    private int cs;
    private int dc;
    private int rst;
    private int busy;
    private boolean is_select;

    public SpiPinConfigInfo(
        String config_name,
        int din, int sck, int cs, int dc, int rst, int busy, boolean is_select ) {
        this.config_name = config_name;
        this.din = din;
        this.sck = sck;
        this.cs = cs;
        this.dc = dc;
        this.rst = rst;
        this.busy = busy;
        this.is_select = is_select;
    }

    public String getConfigName() {
        return config_name;
    }
    public boolean getSelectStatus() { return is_select; }
    public void setSelectStatus(boolean select_status) { is_select = select_status; }

    //sck=13, din=14, cs=15, busy=25, rst=26, dc=27
    public String getConfigVerbose() {
        return "din=" + din + ", sck=" + sck + ", cs=" + cs + ", dc=" + dc + ", rst=" + rst + ", busy=" + busy;
    }
}
