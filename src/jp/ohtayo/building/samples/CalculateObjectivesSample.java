package jp.ohtayo.building.samples;

import jp.ohtayo.building.energyplus.EnergyPlusObjectives;
import jp.ohtayo.commons.io.Csv;
import jp.ohtayo.commons.math.Matrix;
import jp.ohtayo.commons.math.Vector;

/**
 * sample class for simulation using EnergyPlus.
 *
 * @author ohtayo (ohta.yoshihiro@outlook.jp)
 */
public class CalculateObjectivesSample
{
  public static void main(String args[])
  {
    // 設定温度を読み込み
//    Vector variable = new Vector(19, 0.0);
    Vector variable = new Matrix(Csv.read("./in_var.csv")).getRow(0);

    // EnergyPlus計算の準備
    boolean usingDifference = true;
    EnergyPlusObjectives objectives = new EnergyPlusObjectives(variable.get())
//            .setXmlFile(".\\xml\\energyplus.xml")
            .setXmlFile(".\\xml\\energyplus_vrf5z.xml")
            .setIdfOffsets(242 -1, 521 -1)
            .calculate( usingDifference );

    // 評価値の取得と表示
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
