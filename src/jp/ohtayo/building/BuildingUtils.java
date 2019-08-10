package jp.ohtayo.building;

import jp.ohtayo.building.environment.PMV;
import jp.ohtayo.commons.math.Matrix;
import jp.ohtayo.commons.math.Vector;

/**
 * ビルエネルギーや環境設定等のユーティリティクラスです．
 *
 * @author ohtayo (ohta.yoshihiro@outlook.jp)
 */
public class BuildingUtils {

	/**
	 * JからkWhに変換します。<br>
	 * J=W*s=W*h / 3600 = kW*h / 3600 / 1000 <br>
	 * @param J Joule
	 * @return kWh
	 */
	public static double J2kWh(double J)
	{
		return J / 3600 / 1000;
	}

	/**
	 * kWhからJに変換します。<br>
	 * @param kWh kilo Watt Hour
	 * @return J
	 */
	public static double kWh2J(double kWh)
	{
		return kWh * 3600 * 1000;
	}


	/**
	 * 空調冷房能力と空調消費電力から期間の平均COPを算出します。<br>
	 * 冷房能力と消費電力の入力は単位を合わせてください。<br>
	 * @param coolingEnergy 冷房能力[W]の配列
	 * @param electricEnergy 消費電力[W]の配列
	 * @return 期間の平均COP
	 */
	public static double calculateAverageCOP(Vector coolingEnergy, Vector electricEnergy)
	{
		Vector cop = new Vector(coolingEnergy);
		for (int t=0; t<coolingEnergy.length(); t++)
		{
			double temp = coolingEnergy.get(t) / electricEnergy.get(t);
			cop.set(t, temp);
		}
		return cop.mean();
	}

	/**
	 * 電力量データからピーク電力を算出します。<br>
	 * J-\gt;kWhがsamplingPreiodの電力量なので，<br>
	 * @param electricEnergy 消費電力量[J]の配列
	 * @param samplingPeriod サンプリング周期[hour]
	 * @return ピーク電力値[kW]
	 */
	public static double calculatePeakPower(Vector electricEnergy, double samplingPeriod)
	{
		Vector power = new Vector(electricEnergy);
		for (int t=0; t<electricEnergy.length(); t++)
		{
			//電力量[J]をkWhに変換
			double kWh = J2kWh(electricEnergy.get(t));
			//kWhを瞬時電力に変換して格納
			power.set(t, kWh/samplingPeriod);
		}
		//最大電力を返す
		return power.max();
	}

	//Todo:
	//public static double calculatePeakPowerWithin30min(Vector electricEnergy, double samplingPeriod)

	/**
	 * 1カ月の基本料金を計算します．(東京電力高圧・特別高圧業務用電力(500kW未満)の電気料金プラン)
	 * @param peakPower 契約電力[kW]
	 * @param peakUnit 基本料金単価
	 * @param powerFactor 力率(0-1)
	 * @return 1カ月の基本料金[円]
	 */
	public static double calculateBasicElectricityRate(double peakPower, double peakUnit, double powerFactor)
	{
		return peakPower * peakUnit * ( 185-(powerFactor*100) )/100;
	}

	/**
	 * 電気料金を計算します
	 * @param electricEnergy 消費電力量[kWh]
	 * @param unit 電力単価
	 * @return 電気料金
	 */
	public static double calculateElectricityRate(double electricEnergy, double unit)
	{
		return electricEnergy * unit;
	}

	/**
	 * 温度および湿度データからPMVを算出します。<br>
	 * @param temperature 瞬時気温[℃]の配列
	 * @param humidity 瞬時相対湿度[%]の配列
	 * @param Va 風速(固定値)[m/s]
	 * @param Icl 着衣量(固定値)[clo]
	 * @param M 代謝量(固定値)[W/m^2]
	 * @return PMVの配列
	 */
	public static Vector calculatePMV(Vector temperature, Vector humidity, double Va, double Icl, double M)
	{
		Vector pmv = new Vector(temperature);

		for (int t=0; t<temperature.length(); t++)
		{
			double temp = new PMV(temperature.get(t), humidity.get(t), Va, temperature.get(t)+1, Icl, M).get();
			pmv.set(t,temp);
		}
		return pmv;
	}

	/**
	 * 複数ゾーンの温湿度データから各ゾーンのPMVを算出します<br>
	 * @param data 温湿度データ(ゾーン1温度、ゾーン1湿度、ゾーン2温度…の順に列が並ぶデータ)
	 * @param Va 風速[m/s]
	 * @param Icl 着衣量[clo]
	 * @param M 代謝量[W/m^2]
	 * @return 各ゾーンのPMV配列(ゾーン1PMV、ゾーン2PMV…の順に列が並ぶ並ぶデータ)
	 */
	public static Matrix calculateZonePMV(Matrix data, double Va, double Icl, double M)
	{
		int n = (int)(data.columnLength()/2);	//ゾーン数
		Matrix zonePMV = new Matrix(data.length(), n);

		for (int z=0; z<n; z++)
		{
			//温湿度から各時刻のPMVの算出と格納
			Vector temp = calculatePMV(data.getColumn(z*2+0), data.getColumn(z*2+1), Va, Icl, M);
			zonePMV.setColumn(z, temp);
		}
		return zonePMV;
	}


}
