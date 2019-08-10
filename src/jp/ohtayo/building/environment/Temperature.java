package jp.ohtayo.building.environment;

/**
 * 摂氏温度(Celsius)と華氏温度(Fahrenheit)の変換をするクラスです。
 *
 * @author ohtayo (ohta.yoshihiro@outlook.jp)
 */
public class Temperature {
	
	//ラッパー
	public static double C2F(double in)
	{
		return Temperature.celsiusToFahrenheit(in);
	}
	
	public static double F2C(double in)
	{
		return Temperature.fahrenheitToCelsius(in);
	}
	
	/**
	 * 摂氏温度を華氏温度に変換します。
	 * @param in 摂氏温度
	 * @return 変換後の華氏温度
	 */
	public static double celsiusToFahrenheit(double in)
	{
		return (in * 9.0) / 5.0 +32;
	}
	
	/**
	 * 華氏温度を摂氏温度に変換します。
	 * @param in 華氏温度
	 * @return 変換後の摂氏温度
	 */
	public static double fahrenheitToCelsius(double in)
	{
		return (in - 32) * 5.0 / 9.0;
	}
}
