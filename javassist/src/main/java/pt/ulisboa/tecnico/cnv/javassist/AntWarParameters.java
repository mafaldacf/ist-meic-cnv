package pt.ulisboa.tecnico.cnv.javassist;

public class AntWarParameters {
    private int max;
    private int army1;
    private int army2;

    private Long tid;

    public AntWarParameters(int max, int army1, int army2, Long tid) {
        this.max = max;
        this.army1 = army1;
        this.army2 = army2;
        this.tid = tid;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public int getArmy1() {
        return army1;
    }

    public void setArmy1(int army1) {
        this.army1 = army1;
    }

    public int getArmy2() {
        return army2;
    }

    public void setArmy2(int army2) {
        this.army2 = army2;
    }

    public Long getTid() {
        return tid;
    }

    public void setTid(Long tid) {
        this.tid = tid;
    }

    @Override
    public String toString() {
        return "AntWarParameters{" +
                "max=" + max +
                ";army1=" + army1 +
                ";army2=" + army2 +
                '}';
    }
}
