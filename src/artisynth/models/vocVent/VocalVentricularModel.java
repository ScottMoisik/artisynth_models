package artisynth.models.vocVent;

import java.awt.Color;
import java.io.IOException;
import java.util.*;

import artisynth.core.mechmodels.*;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.workspace.RootModel;
import artisynth.core.femmodels.*;
import artisynth.core.femmodels.FemModel.SurfaceRender;
import artisynth.core.gui.ControlPanel;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.render.RenderProps;

public class VocalVentricularModel extends RootModel {
   private MechModel mech;

   private static final boolean SHOW_RENDERING = false;
   private static final boolean USE_VENTRICULAR_FOLDS = false;

   // distance between each vocal fold (mm)
   // Argawal (2002): mean 0.2 modal voice
   private static final double VOCAL_FOLDS_TRANSVERSE_DIST = 0.2;

   // distance between each vent fold (mm)
   private static final double VENT_FOLDS_TRANSVERSE_DIST = 9.0;


   private VocalFoldBuilder vfBuilder = new VocalFoldBuilder();
   private VentFoldBuilder ventBuilder = new VentFoldBuilder();
   private double vocalFoldSaggitalWidth = VocalFoldBuilder.getSaggital();

   private double EPS = 1e-7;  // epsilon value for evaluating near-zero values

   // --- Various cache declarations ---
   // Remember if the x-transpositions have been done
   private boolean xTransposeVocalDone = false;
   private ArrayList<FemNode3d> vocalMedialNodesLeft;
   private ArrayList<FemNode3d> vocalMedialNodesRight;
   private ArrayList<FemMarker> vocalMedialMarkers;


   public ArrayList<FemNode3d> getVocalMedialNodes(boolean isRight) {
      boolean transposed = this.xTransposeVocalDone;
      if (!transposed) {
         System.out.println("WARNING: call build() before getting medial nodes!");
         return new ArrayList<FemNode3d>();
      }
      else if (isRight) {
         return this.vocalMedialNodesLeft;
      }
      else {
         return this.vocalMedialNodesLeft;
      }
   }


   private void setRenderPropsVocal(FemModel3d fem) {
      fem.setSurfaceRendering(SurfaceRender.Shaded);
      RenderProps.setLineColor(fem, Color.DARK_GRAY);
      if (!SHOW_RENDERING)
         RenderProps.setLineWidth(fem, 0);
      RenderProps.setFaceColor(fem, new Color(249/255f, 212/255f, 160/255f));
   }


   private void setRenderPropsVent(FemModel3d fem) {
      setRenderPropsVocal(fem);
      RenderProps.setAlpha(fem, 0.3);
      RenderProps.setFaceColor(fem, new Color (249/255f, 150/255f, 140/255f));
   }


   private HashMap<String, FemModel3d> buildVocalFolds(MechModel mech) {
      boolean isRight = true;

      FemModel3d vofoLeft = vfBuilder.buildFem(!isRight);
      FemModel3d vofoRight = vfBuilder.buildFem(isRight);

      // Before x-translation, both folds form a 'semicircular' cylinder
      // The lateral nodes are those near x=0
      this.vocalMedialNodesLeft = registerMedialNodes(vofoLeft, !isRight);
      this.vocalMedialNodesRight = registerMedialNodes(vofoRight, isRight);

      fixateLateralNodes(vofoLeft);
      fixateLateralNodes(vofoRight);

      // Prevent FEM from traversing along the anterior-posterior axis
      // I'm not sure whether the forces on these edge nodes are reflected or fully damped
      // Disabled as this caused too much damping for oscillation (and not physiologically sound anyway)
//      fixateAntPosNodes(vofoLeft);
//      fixateAntPosNodes(vofoRight);

      // Move each FEM to correct pos along x-axis
      double transposeX = 7.30 + VOCAL_FOLDS_TRANSVERSE_DIST / 2;
      vofoLeft.transformGeometry(new RigidTransform3d(-transposeX, 0, 0));
      vofoRight.transformGeometry(new RigidTransform3d(transposeX, 0, 0));
      this.xTransposeVocalDone = true;

      // Attach controller that drives the vocal fold movement
      VocalFoldsController vofoCon = new VocalFoldsController(
         this.vocalMedialNodesLeft,
         getNodePositions(this.vocalMedialNodesLeft),
         this.vocalMedialNodesRight,
         getNodePositions(this.vocalMedialNodesRight)
      );
      vofoCon.setModel(mech);
      vofoCon.setActive(true);
      addController(vofoCon);

      // Register FEMs to the model
      mech.addModel(vofoLeft);
      mech.addModel(vofoRight);

      // Set appearance
      setRenderPropsVocal(vofoLeft);
      setRenderPropsVocal(vofoRight);

      // Return as hashmap keyed to "left" and "right"
      HashMap<String, FemModel3d> fems = new HashMap<String, FemModel3d>();
      fems.put("left", vofoLeft);
      fems.put("right", vofoRight);
      return fems;
   }


   private HashMap<String, FemModel3d> buildVentFolds(MechModel mech) {
      boolean right = true;
      FemModel3d ventLeft = ventBuilder.buildFem(!right);
      FemModel3d ventRight = ventBuilder.buildFem(right);

      // Before x-translation, both folds form a 'semicircular' cylinder
      // The lateral nodes are those near x=0
      fixateLateralNodes(ventLeft);
      fixateLateralNodes(ventRight);

      // Move vent folds above the vocal folds, then slightly away from each other
      double heightAboveVofo = 0.5;  // Agarwal et al 2002: 0.47cm
      System.out.printf ("VENT_FOLDS_TRANSVERSE_DIST: %.2f%n", VENT_FOLDS_TRANSVERSE_DIST);
      double transposeX = 7.3 + VENT_FOLDS_TRANSVERSE_DIST * 0.5;
      ventLeft.transformGeometry(new RigidTransform3d(-transposeX, heightAboveVofo, 0));
      ventRight.transformGeometry(new RigidTransform3d(transposeX, heightAboveVofo, 0));

      // Register FEMs to the model
      mech.addModel(ventLeft);
      mech.addModel(ventRight);

      // Set appearance
      setRenderPropsVent(ventLeft);
      setRenderPropsVent(ventRight);

      // Return refs as hashmap keyed to "left" and "right"
      HashMap<String, FemModel3d> fems = new HashMap<String, FemModel3d>();
      fems.put("left", ventLeft);
      fems.put("right", ventRight);
      return fems;
   }


   public void build (String[] args) throws IOException {
      super.build(args);

      mech = new MechModel("mech");
      mech.setGravity(0, -9.8, 0);
      mech.setMaxStepSize(0.005);  // 5 ms = 500us
      addModel(mech);


      // Create and attach the FEMs
      HashMap<String, FemModel3d> vofoFems = buildVocalFolds(mech);

      int vocElemsLeft = vofoFems.get("left").numElements ();
      int vocElemsRight = vofoFems.get("right").numElements ();
      System.out.printf("element count, left voc: %d%n", vocElemsLeft);
      System.out.printf("element count, right voc: %d%n", vocElemsRight);


      // Enable collision between VoFs
      mech.setCollisionBehavior (
         vofoFems.get("left"),
         vofoFems.get("right"),
         new CollisionBehavior(true, 0.5)
      );

      if (USE_VENTRICULAR_FOLDS) {
         HashMap<String, FemModel3d> ventFems = buildVentFolds(mech);
         // Create and attach control panels for vent. folds
         ControlPanel panel = VentController.registerControlPanel(mech);
         this.addControlPanel(panel);

         int ventElemsLeft = ventFems.get("left").numElements ();
         int ventElemsRight = ventFems.get("right").numElements ();
         System.out.printf("element count, left vent: %d%n", ventElemsLeft);
         System.out.printf("element count, right vent: %d%n", ventElemsRight);

         // Enable collision between the superior and inferior sets of FEMs
         mech.setCollisionBehavior(
            vofoFems.get("left"),
            ventFems.get("left"),
            new CollisionBehavior(true, 0)
         );
         mech.setCollisionBehavior(
            vofoFems.get("right"),
            ventFems.get("right"),
            new CollisionBehavior(true, 0)
         );
      }

      // Create and attach probes for medial vocal fold markers
      NumericOutputProbe probe = Probes.attachProbes(mech, this.vocalMedialMarkers);
      this.addOutputProbe(probe);
   }


   private boolean isNearZero(double d) {
      return Math.abs(d) <= EPS;
   }


   /**
    * Fixate nodes on the FEM lying on the lateral edge of the folds.
    * Run before x-transposing either half.
    */
   private void fixateLateralNodes(FemModel3d fem) {
      for (FemNode3d n : fem.getNodes()) {
         Point3d pos = n.getPosition();
         if (isNearZero(pos.x)) {
            n.setDynamic(false);
         }
      }
   }

   /**
    * Fixate nodes on the FEM lying on the anterior and posterior extremes of the folds.
    */
   private void fixateAntPosNodes(FemModel3d fem) {
      for (FemNode3d n : fem.getNodes()) {
         Point3d pos = n.getPosition();
         double distFromSaggitalExtreme = Math.abs(pos.z) - vocalFoldSaggitalWidth / 2;
         boolean doFixate = isNearZero(distFromSaggitalExtreme);
         if (doFixate) {
            n.setDynamic(false);
         }
      }
   }


   /**
    */
   private ArrayList<Point3d> getNodePositions(ArrayList<FemNode3d> nodes) {
      ArrayList<Point3d> positions = new ArrayList<Point3d>();
      for (FemNode3d n : nodes) {
         positions.add(n.getPosition());
      }
      return positions;
   }

   /**
    * Finds nodes on the input FEM
    * @param fem
    * @param isRight
    * @return ArrayList of medial nodes
    */
   private ArrayList<FemNode3d> registerMedialNodes(FemModel3d fem, boolean isRight) {
      int flipSign = isRight ? -1 : 1;
      PointList<FemNode3d> nodes = fem.getNodes();

      // First iteration: calculate medial offset
      double medialOffsetX = 0;
      for (FemNode3d n : nodes) {
         double x = Math.abs(n.getPosition().x);
         if (x > medialOffsetX)
            medialOffsetX = x;
      }
      medialOffsetX *= flipSign;

      // Second iteration: find medial-most nodes
      ArrayList<FemNode3d> medialNodes = new ArrayList<FemNode3d>();
      double medialRibbonWidth = .05;
      for (FemNode3d n : fem.getNodes()) {
         double posX = n.getPosition().x;
         boolean isMedial = Math.abs(posX - medialOffsetX) < medialRibbonWidth;
         if (isMedial)
            medialNodes.add(n);
      }

      // Attach markers for medial nodes
      ArrayList<FemMarker> markers = markersFromNodes(medialNodes);
      this.vocalMedialMarkers = markers;
      for (int i=0; i<markers.size(); i++) {
//         for ( FemMarker mkr : markers) {
         FemMarker mkr = markers.get(i);
         mkr.setName(String.format("forceNode%s%d", isRight ? "Right" : "Left", i+1));
         fem.addMarker(mkr);

         // Visualize markers as needed
         if (SHOW_RENDERING) {
            if (isRight) {
               RenderProps.setSphericalPoints(mkr, 0.2, Color.BLUE);
            } else {
               RenderProps.setSphericalPoints(mkr, 0.2, Color.GREEN);
            }
         }
      }
      return medialNodes;
   }

   private ArrayList<FemMarker> markersFromNodes(ArrayList<FemNode3d> nodes) {
      ArrayList<FemMarker> markers = new ArrayList<FemMarker>();
      for (FemNode3d n : nodes) {
         Point3d pos = n.getPosition();
         FemMarker mkr = new FemMarker (/*name=*/null, pos.x, pos.y, pos.z);
         markers.add (mkr);
      }
      return markers;
   }
}
