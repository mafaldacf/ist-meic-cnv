package pt.ulisboa.tecnico.cnv.javassist;

public class FoxRabbitsParameters {

    public static final String GENERATIONS = "generations";
    public static final String WORLD = "world";
    public static final String Scenario = "scenario";

    private int generations;
        private int world;
        private int scenario;

        private Long tid;
        public FoxRabbitsParameters(int generations, int world, int scenario, Long tid)
        {
            this.generations = generations;
            this.world = world;
            this.scenario = scenario;
            this.tid = tid;
        }

        public int getGenerations() {
            return generations;
        }

        public void setGenerations(int generations) {
            this.generations = generations;
        }

        public int getWorld() {
            return world;
        }

        public void setWorld(int world) {
            this.world = world;
        }

        public int getScenario() {
            return scenario;
        }

        public void setScenario(int scenario) {
            this.scenario = scenario;
        }

    public Long getTid() {
        return tid;
    }

    public void setTid(Long tid) {
        this.tid = tid;
    }

    @Override
    public String toString() {
        return "FoxRabbitsParameters{" +
                "generations=" + generations +
                ";world=" + world +
                ";scenario=" + scenario+
                '}';
    }
}
