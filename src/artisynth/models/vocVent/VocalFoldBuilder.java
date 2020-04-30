package artisynth.models.vocVent;

import artisynth.core.femmodels.FemFactory;
import artisynth.core.femmodels.FemModel3d;
import artisynth.core.materials.LinearMaterial;
import artisynth.core.materials.TransverseLinearMaterial;
import maspack.geometry.MeshFactory;
import maspack.geometry.PolygonalMesh;
import maspack.matrix.Vector2d;
import maspack.matrix.Vector3d;

public class VocalFoldBuilder {
   /* Values from Deguchi, Kawahara, Takahashi (2011):
    *
    *   16kPa for membrane, 12kPa for ligament, 8 kPa for TA muscle
    * We can actually have varying Young's modulus by using core.femmodels.ScalarNodalField
    * but we would have to update Artisynth to get that feature. Without the varying the
    * Young's modulus, uniform compression of the vocal fold FEM means that when the superior
    * half of the vocal folds adduct, the inferior half won't "bulge" and medialize. Thus, we
    * won't see any clear phase difference between the superior and inferior halves.
    *
    * For example implementation of varying Young's modulus, see Artisynth modeling guide,
    * section 6.10.4 FEM with variable stiffness:
    * https://www.artisynth.org/manuals/index.jsp?topic=%2Forg.artisynth.doc%2Fhtml%2Fmodelguide%2FCh6.S10.html&cp=1_6_9_3&anchor=SS4
    *
    * Deguchi, S., Kawahara, Y., & Takahashi, S. (2011). Cooperative regulation of vocal fold
    * morphology and stress by the cricothyroid and thyroarytenoid muscles. Journal of Voice,
    * 25(6), e255–e263. https://doi.org/10.1016/j.jvoice.2010.11.006
    */
   static int FEM_QUALITY = 8;  // lower = more detailed

   // Dimensions in millimeters
   private static double vocalFoldSaggitalWidth = 14.0 * 1; // scale down to speedup sim
   private static double vocalFoldTransverseWidth = 7.7;

   public boolean isRightSide = false;

   public static double getWidth() { return vocalFoldTransverseWidth; }
   public static double getSaggital() { return vocalFoldSaggitalWidth; }


   private PolygonalMesh createVocalFoldMesh(boolean isRightSide) {
      double cylHeight_z = vocalFoldSaggitalWidth;
      double cylBigRad_x = vocalFoldTransverseWidth;
      double cylSmallRad_x = 2.01;

      double flipBit = isRightSide ? -1.0 : 1.0;

      // Cut a quadrant of the big cylinder for fold body
      PolygonalMesh cylBig = MeshFactory.createCylinder(cylBigRad_x, cylHeight_z, 50);
      PolygonalMesh boxCrop = MeshFactory.createBox(
         /*dim xyz=*/ 7.5,           7.7,          vocalFoldSaggitalWidth,
         /*off xyz=*/ 7.5/2*flipBit, -7.7/2.0-2.8, 0
      );
      PolygonalMesh quartCyl = MeshFactory.getIntersection(cylBig, boxCrop);

      // Small rectangle from cylinder to origin
      PolygonalMesh boxFlat = MeshFactory.createBox(
         /*dim xyz=*/ 5.3,             2.8,           vocalFoldSaggitalWidth,
         /*off xyz=*/ 5.3/2.0*flipBit, -2.8/2.0-0.01, 0
      );

      // Small cylinder for fold edge
      PolygonalMesh cylSmall = MeshFactory.createCylinder(cylSmallRad_x, cylHeight_z, 50);
      cylSmall.translate (new Vector3d(
         5.3*flipBit,
         -2.0-0.01,
         0)
      );

      // Glue everything together
      PolygonalMesh quartFlat = MeshFactory.getUnion(quartCyl, boxFlat);
      PolygonalMesh vofo = MeshFactory.getUnion(quartFlat, cylSmall);

      return vofo;
   }


   public FemModel3d buildFem(boolean buildRightSide) {
      this.isRightSide = buildRightSide;

      // Generate VoFo FEM from mesh
      String femName = String.format("vocalFold%sFem", this.isRightSide ? "Right" : "");
      FemModel3d fem = new FemModel3d(femName);
      fem.setName(femName);

      PolygonalMesh vofoMesh = createVocalFoldMesh(buildRightSide);
      fem = FemFactory.createFromMesh(fem, vofoMesh, /*quality=*/FEM_QUALITY);

      // FEM material props
      fem.setDensity(10);
//      fem.setParticleDamping(0.0);


      double youngsModulusLongi = 20700; //20700; // E_z of TA muscle, Deguchi et al (2011:258)
      double youngsModulusTrans = 8200;  // E_x of TA muscle, ibid
      double shearModulus = 12000;       // G_yz of TA muscle, ibid
      double poissonRatio = 0.3;         // v, ibid, quoted from Tao & Jiang (2007)
      Vector2d youngsModulus2d = new Vector2d(youngsModulusLongi, youngsModulusTrans);
      Vector2d poissonRatio2d = new Vector2d(poissonRatio, poissonRatio);

      fem.setMaterial (
         new TransverseLinearMaterial(
            youngsModulus2d,
            shearModulus,
            poissonRatio2d,
            /*corotated=*/ false
         )
      );

      return fem;
   }


   public FemModel3d buildFem() {
      return buildFem(false);
   }
}
