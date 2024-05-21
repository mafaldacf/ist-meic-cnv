package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.Iterator;
import java.util.List;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Opcode;
import pt.ulisboa.tecnico.cnv.javassist.AntWarParameters;
import pt.ulisboa.tecnico.cnv.javassist.CompressionParameters;
import pt.ulisboa.tecnico.cnv.javassist.FoxRabbitsParameters;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.Map;

public class ThreadICount extends AbstractJavassistTool {

    private static final String FOXRABBIT_COMPUTATION = "runSimulation";
    private static final String ANTWAR_COMPUTATION = "war";
    private static final String COMPRESSION_COMPUTATION = "process";

    public static Map<Long, Long> nmemoryinsts = new ConcurrentHashMap<Long, Long>();
    public static Map<Long, Long> ninsts = new ConcurrentHashMap<Long, Long>();

    //NOTE: Podemos vir a ter problemas de tamanho. Ns se esta ser reduzido
    public static Map<FoxRabbitsParameters, List<Long>> foxRabbitMetrics = new ConcurrentHashMap<FoxRabbitsParameters, List<Long>>();
    public static Map<AntWarParameters, List<Long>> antWarMetrics = new ConcurrentHashMap<AntWarParameters, List<Long>>();

    public static Map<CompressionParameters, List<Long>> compressionMetrics = new ConcurrentHashMap<CompressionParameters, List<Long>>();


    public ThreadICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void addThreadId() {
        long threadId = Thread.currentThread().getId();
        ninsts.put(threadId, (long) 0);
        nmemoryinsts.put(threadId, (long) 0);
    }

    public static void addWriterThreadId() {
        long threadId = Thread.currentThread().getId();
        ninsts.put(threadId, (long) -1);
        nmemoryinsts.put(threadId, (long) -1);
    }
    
    public static void incInst(int length, int numMemoryAccesses) {
        long threadId = Thread.currentThread().getId();

        if (canInstrument()) {
            try {
                long n = ninsts.get(threadId);
                ninsts.put(threadId, n + length);
                nmemoryinsts.put(threadId, nmemoryinsts.get(threadId) + numMemoryAccesses);
            } catch (Exception e) {
                System.out.println("ninsts: " + ninsts);
                System.out.println("threadId: " + threadId);
                System.out.println("n + length: " + (ninsts.get(threadId) + length));
            }
        }
    }

    public static boolean canInstrument() {
        // can only instrument after and before we reset the counter
        // this only happens when we reach specific function calls: process, runSimulation, war
        long threadId = Thread.currentThread().getId();
        return ninsts.containsKey(threadId) && ninsts.get(threadId) >= 0;

    }

    public static void printStatistics() {
        Long threadId = Thread.currentThread().getId();

        long n = ninsts.get(threadId);
        long mem = nmemoryinsts.get(threadId);
        long work = n+mem;
        System.out.println(String.format("[%s] Number of executed instructions for thread %s: # Instructions = %s, # Memory Accesses = %s, TOTAL WORK = %s",
            ThreadICount.class.getSimpleName(), threadId, n, mem, work));
    }


    public static void registerFoxRabbitMetric()
    {
        Long threadId = Thread.currentThread().getId();
        long nInstructions = ninsts.get(threadId);
        long nMemoryAccesses = nmemoryinsts.get(threadId);


        Iterator<FoxRabbitsParameters> iterator = foxRabbitMetrics.keySet().iterator();
        while( iterator.hasNext() )
        {
            FoxRabbitsParameters frp = iterator.next();
            if ( frp.getTid().equals( threadId ) )
            {
                foxRabbitMetrics.put(frp,List.of(nInstructions, nMemoryAccesses));
                frp.setTid(-1L);
            }
        }
    }

    public static void registerAntWarMetric()
    {
        Long tid = Thread.currentThread().getId();
        long nInstructions = ninsts.get(tid);
        long nMemoryAccesses = nmemoryinsts.get(tid);

        Iterator<AntWarParameters> iterator = antWarMetrics.keySet().iterator();
        while( iterator.hasNext() )
        {
            AntWarParameters awp = iterator.next();
            if ( awp.getTid().equals( tid ) )
            {
                antWarMetrics.put(awp,List.of(nInstructions, nMemoryAccesses));
                awp.setTid(-1L);
            }
        }
    }

    public static void registerCompressionMetric()
    {
        Long tid = Thread.currentThread().getId();
        long nInstructions = ninsts.get(tid);
        long nMemoryAccesses = nmemoryinsts.get(tid);

        Iterator<CompressionParameters> iterator = compressionMetrics.keySet().iterator();
        while( iterator.hasNext() )
        {
            CompressionParameters cp = iterator.next();
            if ( cp.getTid().equals( tid ) )
            {
                compressionMetrics.put(cp,List.of(nInstructions, nMemoryAccesses));
                cp.setTid(-1L);
            }
        }
    }


    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        String computationType = behavior.getName();
        super.transform(behavior);
        if (List.of(FOXRABBIT_COMPUTATION, COMPRESSION_COMPUTATION, ANTWAR_COMPUTATION).contains(computationType))   {
            behavior.insertBefore(String.format("%s.addThreadId();", ThreadICount.class.getName()));
            behavior.insertAfter(String.format("%s.printStatistics();", ThreadICount.class.getName()));
            switch (computationType) {
                case FOXRABBIT_COMPUTATION:
                    behavior.insertAfter(String.format("%s.registerFoxRabbitMetric();", ThreadICount.class.getName()));
                    return;
                case ANTWAR_COMPUTATION:
                    behavior.insertAfter(String.format("%s.registerAntWarMetric();", ThreadICount.class.getName()));
                    return;
                case COMPRESSION_COMPUTATION:
                    behavior.insertAfter(String.format("%s.registerCompressionMetric();", ThreadICount.class.getName()));
                    return;
                default:
                    System.out.println("[THREADICOUNT] Recebi um pedido desconhecido para instrumentatlizar");
            }
        }
    }

    private int getNumLoadStoreInstructions(BasicBlock block) {
        CodeAttribute ca = block.getBehavior().getMethodInfo().getCodeAttribute();
        CodeIterator iterator = ca.iterator();

        int memoryAccesses = 0;
        int position = block.getPosition();
        int length = block.getLength();
        for (int i = position; i < position + length; i++) {
            int opcode = iterator.byteAt(i);
            if (opcode >= Opcode.ILOAD && opcode <= Opcode.SALOAD /* range integers for LOAD operations */
                    || opcode >= Opcode.ISTORE && opcode <= Opcode.SASTORE /* range integers for STORE operations */ ) {
                memoryAccesses++;
            }
        }
        return memoryAccesses;
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        block.behavior.insertAt(block.line, String.format("%s.incInst(%s, %s);", ThreadICount.class.getName(),
                block.getLength(), getNumLoadStoreInstructions(block)));
    }

}
