package artisynth.models.vocVent;

import artisynth.core.femmodels.FemFactory;
import artisynth.core.femmodels.FemModel3d;
import artisynth.core.materials.LinearMaterial;
import maspack.geometry.MeshFactory;
import maspack.geometry.PolygonalMesh;
import maspack.matrix.Vector3d;

public class VentFoldBuilder {

   // Dimensions metch vocal folds (millimeters)
   private static double vocalFoldSaggitalWidth = VocalFoldBuilder.getSaggital();
   private static double vocalFoldTransverseWidth = VocalFoldBuilder.getWidth();

   private static final double youngsModulus = 5000;
   private static final double poissonRatio = 0.3;


   private PolygonalMesh createVentFoldMesh(boolean isRightSide) {
      double cylHeight_z = vocalFoldSaggitalWidth;
      double cylBigRad_x = vocalFoldTransverseWidth;
      double cylSmallRad_x = 2.0;

      double flipBit = isRightSide ? -1.0 : 1.0;

      // Cut a quadrant of the big cylinder for fold body
      PolygonalMesh cylBig = MeshFactory.createCylinder(cylBigRad_x, cylHeight_z, 50);
      PolygonalMesh boxCrop = MeshFactory.createBox(
         /*dimens xyz=*/ 7.5,           7.7,         vocalFoldSaggitalWidth,
         /*offset xyz=*/ 7.5/2*flipBit, 7.7/2.0+2.8, 0
      );
      PolygonalMesh quartCyl = MeshFactory.getIntersection(cylBig, boxCrop);

      // Small rectangle from cylinder to origin
      PolygonalMesh boxFlat = MeshFactory.createBox (
         /*dimens xyz=*/ 5.3,             2.8,          vocalFoldSaggitalWidth,
         /*offset xyz=*/ 5.3/2.0*flipBit, 2.8/2.0+0.01, 0
      );

      // Small cylinder for fold edge
      PolygonalMesh cylSmall = MeshFactory.createCylinder (cylSmallRad_x, cylHeight_z, 50);
      cylSmall.translate (new Vector3d(
         5.3*flipBit,
         2.0+0.01,
         0)
      );

      // Glue everything together
      PolygonalMesh quartFlat = MeshFactory.getUnion(quartCyl, boxFlat);
      PolygonalMesh ventFo = MeshFactory.getUnion(quartFlat, cylSmall);

      return ventFo;
   }

   public FemModel3d buildFem(boolean buildRightSide) {
      // Generate VoFo FEM from mesh
      String femName = String.format("ventFold%sFem", buildRightSide ? "Right" : "");
      FemModel3d fem = new FemModel3d(femName);
      fem.setName(femName);

      PolygonalMesh ventMesh = createVentFoldMesh(buildRightSide);
      fem = FemFactory.createFromMesh(fem, ventMesh, /*quality=*/12);

      // FEM material props
      fem.setDensity(8);
//      fem.setParticleDamping(0.1);
      fem.setMaterial(new LinearMaterial(youngsModulus, poissonRatio));

      return fem;
   }
}
