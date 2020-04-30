package artisynth.models.vocVent;

import java.util.*;

import artisynth.core.femmodels.*;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.probes.NumericOutputProbe;

public class Probes {
   private static String getLogPath(Object obj) {
      String path = "E:\\git\\artisynth_models\\src\\artisynth\\models\\vocVent\\probe.txt";
      System.out.printf("Probe log path: %s%n", path);
      return path;
   }
   
   public static NumericOutputProbe attachProbes(MechModel mech, ArrayList<FemMarker> medialMarkers) {
      // Get markers sorted by Z
      medialMarkers = sortedByZ(medialMarkers);

      ArrayList<FemMarker> midcoronalNodes = new ArrayList<FemMarker>();
      for (FemMarker mkr : medialMarkers) {
         if (Math.abs(mkr.getPosition ().z) < 0.1) {
            midcoronalNodes.add(mkr);
            break;
         }
      }
      System.out.printf("Nodes: %d%n", midcoronalNodes.size());
      
      String[] propPaths = new String[midcoronalNodes.size()];
      
      FemMarker marker = null;
      for (int i=0; i<midcoronalNodes.size (); i++) {
         FemMarker mkr = midcoronalNodes.get(i);
         String parentFemName = mkr.getParent().getParent().getName ();
         String path = String.format ("models/%s/markers/%s:position", parentFemName, mkr.getName ());
         propPaths[i] = path;
         marker = mkr;
      }
      
      NumericOutputProbe probe = new NumericOutputProbe (
         mech, 
         propPaths,
         getLogPath(marker),
         /*time step (s)=*/ 0.01
      );
      
      probe.setName("Medial Adduction");
      probe.setDefaultDisplayRange (-300, 300);
      probe.setStopTime (10);
      return probe;
   }
   
   public static ArrayList<FemMarker> sortedByZ(ArrayList<FemMarker> medialMarkers) {
      TreeMap<Double, FemMarker> tree = new TreeMap<Double, FemMarker>();
      for (FemMarker mkr : medialMarkers) 
         tree.put (mkr.getPosition ().z, mkr);
      
      ArrayList<FemMarker> sortedMarkers = new ArrayList<FemMarker>();
      for (FemMarker mkr : tree.values())
         sortedMarkers.add (mkr);
      
      return sortedMarkers;
   }
}
