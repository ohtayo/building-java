package jp.ohtayo.building.energyplus;

import jp.ohtayo.commons.io.ConfigBase;

/**
 * EnergyPlusの実行に必要なコンフィグ設定をXMLで外部に保存・読込するクラスです。<br>
 *
 * @author ohtayo (ohta.yoshihiro@outlook.jp)
 */
public class ConfigEnergyPlus extends ConfigBase {

	// EnergyPlusのexeのあるフォルダ名
	public String exeFolder;
	// weatherファイル名
	public String weatherFile;
	// EnergyPlusの作業フォルダの親フォルダ
	public String idfBaseFolder;
	// idfファイル名
	public String idfFile;
}
