package artisynth.models.vocVent;

import artisynth.core.mechmodels.*;
import artisynth.core.gui.ControlPanel;

public class VentController {
   public static ControlPanel registerControlPanel(MechModel mech) {
      ControlPanel panel = new ControlPanel("ventFoldsControlPanel");

      panel.addWidget(
         "Left vent. fold transparency",
         mech,
         "models/ventFoldFem:renderProps.alpha"
      );

      panel.addWidget(
         "Right vent. fold transparency",
         mech,
         "models/ventFoldRightFem:renderProps.alpha"
      );

      return panel;
   }
}
