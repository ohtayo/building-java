package jp.ohtayo.building.samples;

import jp.ohtayo.building.energyplus.EnergyPlusObjectives;
import jp.ohtayo.commons.math.Vector;

/**
 * sample class for simulation using EnergyPlus.
 *
 * @author ohtayo <ohta.yoshihiro@outlook.jp>
 */
public class CalculateObjectivesSample
{
  public static void main(String args[])
  {
    Vector variable = new Vector(19, 0.0);
    boolean usingDifference = true;
    EnergyPlusObjectives objectives = new EnergyPlusObjectives(variable.get(), usingDifference, ".\\xml\\energyplus.xml");
    double power = objectives.calculateTotalElectricEnergy();
    double peak = objectives.calculatePeakElectricEnergy();
    double pmv = objectives.calculateAveragePMV();
    double outofpmv = objectives.countConstraintExceededTimesOfPMV();
    double setpointdif = objectives.countConstraintExceededTimesOfSetpointTemperature();
    System.out.println(power);
    System.out.println(peak);
    System.out.println(pmv);    System.out.println(outofpmv);
    System.out.println(setpointdif);
  }
}
