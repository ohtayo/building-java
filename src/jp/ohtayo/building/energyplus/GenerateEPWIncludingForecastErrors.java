package jp.ohtayo.building.energyplus;

import jp.ohtayo.commons.io.Csv;

import java.util.Calendar;

public class GenerateEPWIncludingForecastErrors {
  public static void main(String[] args)
  {
    double[][] csv = Csv.read("GeneratedForecastOutsideTemp.csv");
    // 書き換え日付の定義
    Calendar baseDate = Calendar.getInstance();
    baseDate.set(2006, 8, 21, 1, 0);

    for(int i=1; i<=10; i++) {
      String suffix;
      if(i<=5)  suffix = "up" + i;
      else      suffix = "down" + (i-5);

      // energyplusコントロールクラスのインスタンスを作る
      String xmlFile = "..\\jMetal\\xml\\energyplus_"+suffix+".xml";
      ControlEnergyPlus energyplus = new ControlEnergyPlus(xmlFile);

      // epwの書き換え実行
      energyplus.rewriteEPWFileFromCsv(baseDate, csv[i-1]);
    }
  }
}
