package artisynth.models.vocVent;

import java.util.ArrayList;
import java.util.HashMap;

import artisynth.core.femmodels.FemNode3d;
import artisynth.core.modelbase.ControllerBase;
import maspack.matrix.Point3d;
import maspack.matrix.Vector3d;


public class VocalFoldsController extends ControllerBase {
   private static final double VOFO_SAGGITAL_LENGTH = VocalFoldBuilder.getSaggital();

   // For caching references to nodes
   private ArrayList<FemNode3d> leftNodes;
   private ArrayList<FemNode3d> rightNodes;

   // To remember the original location of each node; used when resetting simulation
   private ArrayList<Point3d> leftOrigPos;
   private ArrayList<Point3d> rightOrigPos;


   VocalFoldsController(
         ArrayList<FemNode3d> leftMedialNodes,
         ArrayList<Point3d> leftMedialOrigPos,
         ArrayList<FemNode3d> rightMedialNodes,
         ArrayList<Point3d> rightMedialOrigPos) {
      leftNodes = sortedNodesDescByZ(leftMedialNodes);
      leftOrigPos = sortedPointsDescByZ(leftMedialOrigPos);
      rightNodes = sortedNodesDescByZ(rightMedialNodes);
      rightOrigPos = sortedPointsDescByZ(rightMedialOrigPos);
   }

   private ArrayList<Point3d> sortedPointsDescByZ(ArrayList<Point3d> pointList) {
      ArrayList<Point3d> dup = new ArrayList<Point3d>();
      for (Point3d p : pointList) {
         if (dup.size() == 0) {
            dup.add(new Point3d(p.x, p.y, p.z));
            continue;
         }
         Point3d point = new Point3d(p.x, p.y, p.z);
         boolean added = false;
         for (int i=0; i<dup.size(); i++) {
            if (dup.get (i).z < point.z) {
               dup.add(i, point);
               added = true;
               break;
            }
         }
         if (!added)
            dup.add(point);
      }
      return dup;
   }

   private ArrayList<FemNode3d> sortedNodesDescByZ(ArrayList<FemNode3d> nodeList) {
      // Lazy insert sort with linked list
      ArrayList<FemNode3d> dup = new ArrayList<FemNode3d>();
      for (FemNode3d node : nodeList) {
         if (dup.size() == 0) {
            dup.add(node);
            continue;
         }
         boolean added = false;
         for (int i=0; i<dup.size(); i++) {
            if (dup.get(i).getPosition().z < node.getPosition().z) {
               dup.add(i, node);
               added = true;
               break;
            }
         }
         if (!added)
            dup.add(node);
      }
      return dup;
   }

   private double integrateXOverZ(ArrayList<FemNode3d> nodes) {
      double prevX = nodes.get(0).getPosition().x;
      double prevZ = nodes.get(0).getPosition().z;

      double area = 0;

      // start from idx1, idx0 already used to init
      for (int i=1; i<nodes.size() - 1; i++) {
         Point3d pos = nodes.get(i).getPosition();

         // Z is descending
         double stepZ = Math.abs(pos.z - prevZ);

         double x = Math.abs(pos.x);
         double avgX = (x + prevX) / 2;

         area += stepZ * avgX;

         prevX = pos.x;
         prevZ = pos.z;
      }
      return area;
   }

   private double getInterfoldArea() {
      double leftArea = integrateXOverZ(this.leftNodes);
      double rightArea = integrateXOverZ(this.rightNodes);
      return Math.max(leftArea + rightArea, 0);
   }

   @Override
   public void initialize(double t0) {
      System.out.println("Initializing vocal folds movement controller.");
      for (int i=0; i<leftNodes.size(); i++) {
         FemNode3d node = leftNodes.get(i);
         Point3d pos = leftOrigPos.get(i);
         node.setPosition(pos);
      }

      for (int i=0; i<rightNodes.size(); i++) {
         FemNode3d node = rightNodes.get(i);
         Point3d pos = rightOrigPos.get(i);
         node.setPosition(pos);
      }
   }


   @Override
   public void apply(double t0, double t1) {
//      if (t1 > 0.1)
//         return;
      double openSpaceArea = getInterfoldArea();
      double tubeArea = 0.5 * Math.PI * 4.0 * 4.0;

      double impingeArea = Math.max(tubeArea - openSpaceArea, 0);
      double force = 30 * impingeArea;

      System.out.printf("%f %.4f%n", t1, openSpaceArea);

      for (FemNode3d node : leftNodes) {
         Vector3d forceVec = getForceVector(node.getPosition().z, false);
         forceVec.scale(force);
         node.addForce(forceVec);
      }

      for (FemNode3d node : rightNodes) {
         Vector3d forceVec = getForceVector(node.getPosition().z, true);
         forceVec.scale(force);
         node.addForce(forceVec);
      }
   }

   private Vector3d getForceVector(double z, boolean invertX) {
      double zScale = z / VOFO_SAGGITAL_LENGTH * 0.4;   // [-0.5, 0.5]
      double magnitude = Math.cos(zScale * Math.PI);

      double invert = invertX ? 1 : -1;

      Vector3d vec = new Vector3d(
         invert * -0.02,
         magnitude,
         0
      );
      return vec;
   }
}
