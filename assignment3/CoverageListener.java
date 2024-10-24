import gov.nasa.jpf.jvm.ClassFile;
import gov.nasa.jpf.report.Publisher;
import gov.nasa.jpf.report.PublisherExtension;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.search.SearchListener;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.VMListener;
import gov.nasa.jpf.ListenerAdapter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

public class CoverageListener extends ListenerAdapter {

    private TreeMap<String, Set<Integer>> coverageMap = new TreeMap<>();

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
        String location = instructionToExecute.getFileLocation();
        if (location != null && !location.startsWith("java/") && !location.startsWith("sun/") && !location.startsWith("gov/")) {
            String[] parts = location.split(":");
            if (parts.length == 2) {
                String className = parts[0];
                int lineNumber = Integer.parseInt(parts[1]);

                coverageMap.putIfAbsent(className, new TreeSet<>());
                coverageMap.get(className).add(lineNumber);
            }
        }
    }

    @Override
    public void searchFinished(Search search) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./results/report.txt"))) {
            for (String className : coverageMap.keySet()) {
                for (int lineNumber : coverageMap.get(className)) {
                    writer.write(className + ":" + lineNumber);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
