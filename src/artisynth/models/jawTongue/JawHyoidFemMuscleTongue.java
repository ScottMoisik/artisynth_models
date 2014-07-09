package artisynth.models.jawTongue;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import maspack.interpolation.Interpolation;
import maspack.interpolation.Interpolation.Order;
import maspack.matrix.RigidTransform3d;
import maspack.properties.PropertyMode;
import maspack.render.RenderProps;
import maspack.render.RenderProps.LineStyle;
import maspack.render.RenderProps.PointStyle;
import artisynth.core.femmodels.FemElement;
import artisynth.core.femmodels.FemMuscleModel;
import artisynth.core.femmodels.FemNode;
import artisynth.core.femmodels.FemNode3d;
import artisynth.core.femmodels.MuscleBundle;
import artisynth.core.femmodels.FemModel.SurfaceRender;
import artisynth.core.femmodels.FemModel.IncompMethod;
import artisynth.core.materials.GenericMuscle;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.RigidBodyConnector;
import artisynth.core.mechmodels.MechSystemSolver.Integrator;
import artisynth.core.probes.NumericInputProbe;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.RootModel;
import artisynth.models.tongue3d.FemMuscleTongueDemo;
import artisynth.models.tongue3d.HexTongueDemo;

public class JawHyoidFemMuscleTongue extends BadinJawHyoidTongue {

   public static final double hyoidTransStiffness = 200;
   public static final double hyoidRotStiffness = 10000;

   public JawHyoidFemMuscleTongue () {
      super();
   }

   public JawHyoidFemMuscleTongue (String name) {
      super(name);

      myJawModel.setIntegrator(Integrator.ConstrainedBackwardEuler);
      // myJawModel.setMaxStepSizeSec (0.002);

      // FrameSpring hyoidSpring = myJawModel.frameSprings().get(0);
      // hyoidSpring.setStiffness(hyoidTransStiffness);
      // hyoidSpring.setRotaryStiffness(hyoidRotStiffness);
      // myJawModel.updateFrameMarkersRefPos();

      setupTongueRenderProps();
      
      this.setAdaptiveStepping (false);
      FemMuscleTongueDemo.addExcitersFromFiles (tongue, ArtisynthPath.getSrcRelativePath (FemMuscleTongueDemo.class, "exciters/"));
      
   }

   public void setupTongueRenderProps() {
      if (tongue == null)
         return;

      RenderProps.setPointStyle(tongue, PointStyle.SPHERE);
      RenderProps.setPointRadius(tongue, 1);
      RenderProps.setLineWidth(tongue, 3);
      RenderProps.setLineWidth(tongue.getElements(), 2);
      RenderProps.setLineStyle(tongue.getMuscleBundles(), LineStyle.LINE);
      RenderProps.setLineRadius(tongue.getMuscleBundles(), 0.3);
      RenderProps.setVisible(tongue.getNodes(), false);
      for (FemNode3d n : tongue.getNodes()) {
         RenderProps.setVisibleMode(n, PropertyMode.Inherited);
      }
      RenderProps.setVisible(tongue.getElements(), true);
      RenderProps.setVisible(tongue.getMuscleBundles(), true);

   }

   public void addTongueToJaw() {

      tongue = FemMuscleTongueDemo.createFemMuscleTongue(useLinearMaterial);

      GenericMuscle mat = new GenericMuscle();
      // mat.setMaxStress(60000);
      tongue.setMuscleMaterial(mat);

      tongue.scaleDistance(m2mm);
      if (useIncompressibleConstraint) {
         tongue.setIncompressible(IncompMethod.AUTO);
      }
      else {
         tongue.setIncompressible(IncompMethod.OFF);
      }

      // stiffener = new FemMuscleStiffener(tongue);

      RigidTransform3d tongueBackward = new RigidTransform3d();
      tongueBackward.p.x = 2.0; // mm
      tongue.transformGeometry(tongueBackward);

      myJawModel.addModel(tongue);

      FemMuscleTongueDemo.addExciters(tongue);
   }

   public static void setBlackWhite(MechModel mech, FemMuscleModel fem) {
      RenderProps.setFaceColor(fem, Color.WHITE);
      RenderProps.setLineColor(fem, Color.BLACK);
      fem.setSurfaceRendering(SurfaceRender.None);
      RenderProps.setLineWidth(fem, 2);
      RenderProps.setLineWidth(fem.getElements(), 2);

      for (RigidBody body : mech.rigidBodies())
      {
         RenderProps.setFaceColor(body, Color.WHITE);
         RenderProps.setLineColor(body, Color.BLACK);
         RenderProps.setDrawEdges(body, true);
         RenderProps.setLineWidth(body, 2);
         body.setAxisLength(0);
      }

      for (RigidBodyConnector con : mech.rigidBodyConnectors())
      {
         RenderProps.setVisible(con, false);
      }

      RenderProps.setVisible(mech.axialSprings(), false);
      RenderProps.setVisible(mech.multiPointSprings(), false);
      RenderProps.setVisible(mech.frameMarkers(), false);

      fem.setElementWidgetSize(1);

      for (FemElement e : fem.getElements()) {
         for (FemNode n : e.getNodes()) {
            if (n.getPosition().y < -1e-4) {
               RenderProps.setVisible(e, false);
               break;
            }
         }
      }

      for (MuscleBundle b : fem.getMuscleBundles()) {
         RenderProps.setVisible(b, false);
         RenderProps.setVisibleMode(b.getFibres(), PropertyMode.Inherited);
         for (Muscle m : b.getFibres()) {
            RenderProps.setVisibleMode(m, PropertyMode.Inherited);
         }
      }
   }

   public void attach(DriverInterface driver) {
      super.attach(driver);

      removeAllInputProbes();
      removeAllOutputProbes();

      File workingDir = new File(ArtisynthPath.getHomeDir() + "/r/");
      if (workingDir.exists()) {
         ArtisynthPath.setWorkingDir(workingDir);
      }

      if (myControlPanel != null) {
         FemMuscleTongueDemo.setSliderRange(
            myControlPanel, "excitation", 0,
            FemMuscleTongueDemo.muscleExcitationSliderMax);
      }
      
      
//    HexTongueDemo.setActivationColor (tongue);
      HexTongueDemo.addMuscleExciterProbes (this, tongue, Order.Linear);  // default duration = 1
      HexTongueDemo.addMuscleExciterProbe (this, myJawModel.getMuscleExciters ().get ("bi_ad"), Order.Linear, /*duration=*/1.0);
      HexTongueDemo.addMuscleExciterProbe (this, myJawModel.getMuscleExciters ().get ("bi_ip"), Order.Linear, /*duration=*/1.0);
      HexTongueDemo.addMuscleExciterProbe (this, myJawModel.getMuscleExciters ().get ("bi_close"), Order.Linear, /*duration=*/1.0);
      
   }

}
