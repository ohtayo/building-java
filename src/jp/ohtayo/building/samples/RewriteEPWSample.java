package jp.ohtayo.building.samples;

import jp.ohtayo.building.energyplus.ControlEnergyPlus;

import java.util.Calendar;

public class RewriteEPWSample {
  public static void main(String[] args)
  {
    // energyplusコントロールクラスのインスタンスを作る
//    String xmlFile = ".\\xml\\energyplus_upward.xml";
    String xmlFile = ".\\xml\\energyplus_downward.xml";
    ControlEnergyPlus energyplus = new ControlEnergyPlus(xmlFile);

    // 書き換え日付の定義
    Calendar baseDate = Calendar.getInstance();
    baseDate.set(2006, 7, 15, 1, 0);
    Calendar targetDate = Calendar.getInstance();
    targetDate.set(2006, 8, 21, 1, 0);

    // epwの書き換え実行
    energyplus.rewriteEPWFile(baseDate, targetDate);
  }
}
