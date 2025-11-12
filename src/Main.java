import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Main {
    //Ready queue holding the processes ready to be executed
    static Queue<PCBClass> readyQueue = new LinkedList<>();
    //holds reference to the process being executed on the CPU
    static PCBClass currProcess = null;
    //tracks the simulation time in ticks
    static int currTime = 0;
    //starts at P_ID 3 because the next process to be created is 3
    static int nextPID = 3;
    //Track all processes
    static List<PCBClass> processes = new ArrayList<>();
    //Gantt chart to track execution timeline
    static List<GanttEntry> ganttChart = new ArrayList<>();

    static PCBClass p1 = new PCBClass(1, "PA");  // P1
    static PCBClass p2 = new PCBClass(2, "PB");  // P2

    /**
     * Selects and removes the highest priority process from the ready queue.
     * Implements priority scheduling by searching through the entire ready queue
     * to find the process with the smallest priority number (highest priority).
     * <p>
     * Priority levels: PC (1) > PA (2) > PB (3)
     * <p>
     * If multiple processes have the same priority, the first one encountered
     * during iteration is selected, maintaining FIFO order for round-robin
     * scheduling within the same priority level.
     *
     * @return the PCBClass object with the highest priority (smallest priority number),
     *         or null if the ready queue is empty
     */
    static PCBClass nextProcess(){
        return readyQueue.stream()
                .min(Comparator.comparingInt(PCBClass::getPriority))
                .map(p -> {
                    readyQueue.remove(p);
                    return p;
                })
                .orElse(null);
    }

    /**
     * Wrapper class to indicate whether simulation should continue
     */
    static class ProcessCheckResult {
        boolean shouldContinue;
        int ganttStartTime;

        ProcessCheckResult(boolean shouldContinue, int ganttStartTime) {
            this.shouldContinue = shouldContinue;
            this.ganttStartTime = ganttStartTime;
        }
    }

    /**
     * Checks if a process is ready to execute and selects next if needed.
     * @param ganttStartTime current Gantt chart start time
     * @return ProcessCheckResult containing continuation status and updated start time
     */
    static ProcessCheckResult processChecker(int ganttStartTime) {
        if (currProcess == null) {
            currProcess = nextProcess();

            if (currProcess == null) {
                // No processes available - simulation complete
                return new ProcessCheckResult(false, ganttStartTime);
            }

            // New process selected - new Gantt entry starts
            return new ProcessCheckResult(true, currTime);
        }

        // Current process still running - no change
        return new ProcessCheckResult(true, ganttStartTime);
    }

    /**
     * Executes one tick of the current process and handles any forking.
     */
    static void runTicks() {
        currProcess.executeTicks();

        // Check for forking
        if (ForkClass.doFork(currProcess)) {
            PCBClass newProcess = ForkClass.forkProcess(currProcess, nextPID);
            if (newProcess != null) {
                readyQueue.add(newProcess);
                processes.add(newProcess);
                nextPID++;
            }
        }
    }

    /**
     * Handles signal delivery based on process state.
     * @param needsSwitch whether a context switch is about to occur
     * @return true if signal should be delivered to next process
     */
    static boolean handleSignal(boolean needsSwitch) {
        if (currTime % 3 == 0) {
            if (needsSwitch) {
                return true; // Signal for next process
            } else {
                currProcess.addSignal(); // Current process gets it
            }
        }
        return false;
    }

    /**
     * Performs context switch: records Gantt entry, requeues process, selects next.
     * @param ganttStartTime when current process started its execution slice
     * @param signalPending whether a signal needs to be delivered to next process
     * @return new ganttStartTime for the next process
     */
    static int doSwitch(int ganttStartTime, boolean signalPending) {
        // Record Gantt entry
        ganttChart.add(new GanttEntry(ganttStartTime, currTime, currProcess.getP_ID()));

        // Requeue if not complete
        if (!currProcess.isComplete()) {
            currProcess.resetQuantum();
            readyQueue.add(currProcess);
        }

        // Select next process
        currProcess = nextProcess();

        // Deliver pending signal if needed
        if (signalPending && currProcess != null) {
            currProcess.addSignal();
        }

        // Return new start time
        return (currProcess != null) ? currTime : -1;
    }

    /**
     * Prints the Gantt Chart showing the process execution timeline.
     * Displays each process with its start and end times.
     */
    static void printGanttChart() {
        System.out.println("Gantt Chart:");
        System.out.println("+----------+------------+----------+");
        System.out.println("| Process  | Start Time | End Time |");
        System.out.println("+----------+------------+----------+");


        ganttChart.forEach(entry ->
                System.out.printf("| P%-7d | %-10d | %-8d |%n",
                        entry.processId, entry.startTime, entry.endTime)
        );

        System.out.println("+----------+------------+----------+");
    }

    /**
     * Prints the total number of signals received by each process.
     * Displays processes in order of their PID.
     */
    static void printSignalCounts() {
        System.out.println("\nSignal Count per Process:");

        // Calculate total using stream reduce
        int totalSignals = processes.stream()
                .mapToInt(PCBClass::getReceivedSignals)
                .sum();

        // Print each process
        processes.forEach(process ->
                System.out.printf("P%d: %d signals%n",
                        process.getP_ID(), process.getReceivedSignals())
        );

        System.out.printf("\nTotal signals: %d%n", totalSignals);
    }

    public static void main(String[] args) {
        // Initialize
        processes.add(p1);
        processes.add(p2);
        currProcess = p1;
        readyQueue.add(p2);

        int ganttStartTime = 0;


        while (currProcess != null || !readyQueue.isEmpty()) {
            // Check if we have a process to run
            ProcessCheckResult result = processChecker(ganttStartTime);
            if (!result.shouldContinue) break; // No processes left
            ganttStartTime = result.ganttStartTime;

            // Execute one tick and increment the current time
            runTicks();
            currTime++;

            // Check if context switch needed
            boolean needsSwitch = currProcess.isComplete() || currProcess.getCurrQuantum() >= 4;

            // Handle signals
            boolean signalPending = handleSignal(needsSwitch);

            // Perform context switch if needed
            if (needsSwitch) {
                ganttStartTime = doSwitch(ganttStartTime, signalPending);
            }
        }

        // Print results
        printGanttChart();
        printSignalCounts();
    }
}

class PCBClass {
    private int P_ID;
    private String program;
    private int executedTicks;
    private int totalTicks;
    private int priority;
    private int currQuantum;
    private int receivedSignals;
    private int forkCounter;

    public PCBClass(int p_ID, String program){
        this.P_ID = p_ID;
        this.program = program;
        this.executedTicks = 0;
        this.totalTicks = 0;
        this.currQuantum = 0;
        this.receivedSignals = 0;
        this.forkCounter = 0;

        switch (program) {
            case "PA" -> {
                this.priority = 2;
                this.totalTicks = 10;
            }
            case "PB" -> {
                this.priority = 3;
                this.totalTicks = 7;
            }
            case "PC" -> {
                this.priority = 1;
                this.totalTicks = 5;
            }
        }
    }

    public boolean isComplete(){
        return executedTicks >= totalTicks;
    }

    public void resetQuantum(){
        this.currQuantum = 0;
    }

    public String toString(){
        return "P" + P_ID + "(" + program + ")";
    }
    public int getExecutedTicks(){
        return executedTicks;
    }
    public int getTotalTicks(){
        return totalTicks;
    }
    public int getPriority(){
        return priority;
    }
    public int getCurrQuantum(){
        return currQuantum;
    }
    public int getReceivedSignals(){
        return receivedSignals;
    }
    public int getForkCounter(){
        return forkCounter;
    }
    public void executeTicks(){
        executedTicks++;
        currQuantum++;
    }
    public void addSignal(){
        receivedSignals++;
    }
    public void incrementForkCounter(){
        forkCounter++;
    }
    public String getProgram() {
        return program;
    }
    public int getP_ID() {
        return P_ID;
    }
}

class ForkClass {

    /**
     * Checks if a process should fork at its current execution tick.
     * PA and PB processes fork every 3 ticks of execution (at 3, 6, 9).
     * PC processes do not fork.
     *
     * @param process the process to check
     * @return true if the process should fork now, false otherwise
     */
    public static boolean doFork(PCBClass process) {
        int executedTicks = process.getExecutedTicks();
        String program = process.getProgram();

        // PC doesn't fork
        if (program.equals("PC")) {
            return false;
        }

        // Check if at a forking milestone (3, 6, or 9 executed ticks)
        if (executedTicks % 3 == 0 && executedTicks > 0) {
            // Calculate how many forks should have happened by now
            int expectedForks = executedTicks / 3;

            // Only fork if we haven't already forked at this milestone
            return process.getForkCounter() < expectedForks;
        }

        return false;
    }

    /**
     * Creates a new forked process based on parent's program type.
     * PA processes fork PB processes.
     * PB processes fork PC processes.
     * PC processes do not fork.
     *
     * @param parent the parent process doing the forking
     * @param nextPID the PID to assign to the new process
     * @return the newly created PCBClass, or null if parent cannot fork
     */
    public static PCBClass forkProcess(PCBClass parent, int nextPID) {
        String parentProgram = parent.getProgram();

        // Determine child program based on parent using switch expression
        String childProgram = switch (parentProgram) {
            case "PA" -> "PB";  // PA forks PB
            case "PB" -> "PC";  // PB forks PC
            default -> null;    // PC doesn't fork
        };

        // PC doesn't fork
        if (childProgram == null) {
            return null;
        }

        // Create the new process
        PCBClass newProcess = new PCBClass(nextPID, childProgram);

        // Increment parent's fork counter
        parent.incrementForkCounter();

        return newProcess;
    }
}

class GanttEntry {
    int startTime, endTime, processId;

    public GanttEntry(int startTime, int endTime, int processId) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.processId = processId;
    }
}