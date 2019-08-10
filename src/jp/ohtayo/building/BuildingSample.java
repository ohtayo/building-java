package jp.ohtayo.building;

import jp.ohtayo.building.energyplus.EnergyPlusObjectives;
import jp.ohtayo.commons.math.Vector;

/**
 * sample class for simulation using EnergyPlus.
 *
 * @author ohtayo (ohta.yoshihiro@outlook.jp)
 */
public class BuildingSample
{
  public static void main(String args[])
  {
    Vector variable = new Vector(20, 0.1);
    boolean usingDifference = true;
    EnergyPlusObjectives objectives = new EnergyPlusObjectives(variable.get(), usingDifference);
    double power = objectives.calculateTotalElectricEnergy();
    double peak = objectives.calculatePeakElectricEnergy();
    double pmv = objectives.calculateAveragePMV();
    double outofpmv = objectives.countConstraintExceededTimesOfPMV();
    double setpointdif = objectives.countConstraintExceededTimesOfSetpointTemperature();
    System.out.println(power);
    System.out.println(peak);
    System.out.println(pmv);
    System.out.println(outofpmv);
    System.out.println(setpointdif);
  }
}
