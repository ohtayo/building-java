package jp.ohtayo.building.energyplus;

import jp.ohtayo.building.BuildingUtils;
import jp.ohtayo.commons.log.Logging;
import jp.ohtayo.commons.math.Matrix;
import jp.ohtayo.commons.math.Numeric;
import jp.ohtayo.commons.math.Vector;
import jp.ohtayo.commons.util.Cast;

import java.util.Calendar;

/**
 * EnergyPlusのモデルによる最適化計算の目的関数算出クラスです．
 *
 * @author ohtayo <ohta.yoshihiro@outlook.jp>
 */
public class EnergyPlusObjectives {

    // config
    private String energyPlusConfigFile = ".\\xml\\energyplus.xml";

    // definition for IDF file
    private int idfDateOffset = 154 -1;			//idfファイルの最初の日付の行数-1
    private int idfTemperatureOffset = 429 -1;	//idfファイルの最初の温度の行数-1
    private int evaluationMonth = 8;
    private int evaluationDay = 21;

    // definition for result file
    //PMVの抽出：35行目から103行が6:00-23:00のデータ、5-6列目にゾーン1, 2のPMV
    private int hoursInOneDay = 24;
    private int timestepsPerHour = 6;
    private int evaluationStartTimeForComfortLevel = 7*timestepsPerHour-1;	//7:00
    private int evaluationEndTimeForComfortLevel = 22*timestepsPerHour-1;	//22:00
    private int evaluationStartTimeForEnergy = 0;	//0:10
    private int evaluationEndTimeForEnergy = 24*timestepsPerHour-1;		//24:00
    private int evaluationStartTimeForTemperatureSetting = 5*timestepsPerHour;	//0:10
    private int evaluationEndTimeForTemperatureSetting = 24*timestepsPerHour-1;		//24:00
    /*
    private static int groundPMVColumnNumber		= 10;
    private static int middlePMVColumnNumber		= 11;
    private static int topPMVColumnNumber			= 12;
    private static int coolingEnergyColumnNumber	= 13;
    private static int electricEnergyColumnNumber	= 14;
    */
    //private static int[] columnsOfPMV = {10, 11, 12};
    private int[] columnsOfPMV = {11};
    private int[] columnsOfCoolingEnergy = {14};
    private int[] columnsOfElectricEnergy = {13};
    private int[] columnsOfTemperatureSetting = {3};

    private int numberOfVariables = 20;    // 5:00～24:00を1時間毎に変更する．変数長は20
    private int VARIABLE_LENGTH_MAX = 25;    // 0:00～24:00を1時間毎に変更する．変数長最大値

    // Todo: variableのインスタンス化と保持
    private Matrix result;

    public double[][] get(){ return result.get();  }

    /**
     * constructor.
     * @param data EenrgyPlusの実行結果から得られたデータ
     */
    public EnergyPlusObjectives(Matrix data)
    {
        result = new Matrix(data);
    }
    /**
     * constructor.
     * @param variable 変数
     */
    public EnergyPlusObjectives(double[] variable)
    {
        executeEnergyPlusSimulation(variable, true);
    }

    /**
     * constructor.
     * @param variable 変数
     * @param usingDifference 設計変数を設定温度に変換するときに差分とするか
     */
    public EnergyPlusObjectives(double[] variable, boolean usingDifference)
    {
        executeEnergyPlusSimulation(variable, usingDifference);
    }

    /**
     * 目的関数を計算します<br>
     * @param variable 変数
     * @param usingDifference 設計変数を設定温度に変換するときに差分とするか
     */
    public void executeEnergyPlusSimulation(double[] variable, boolean usingDifference)
    {
        //0. 入力値チェック
        if( variable.length != numberOfVariables )	Logging.logger.severe("illegal variable length.");

        //1. variableの設定温度組合せへの変換
        double initialValue = 25.0;
        double[] temperature;
        if(usingDifference)
            temperature = variableToTemperatureSettingUsingDifference(variable, initialValue,25-numberOfVariables, hoursInOneDay+1);
        else
            temperature = variableToTemperatureSettingUsingEachValue(variable, initialValue,25-numberOfVariables, hoursInOneDay+1);
        System.out.println(temperature);

        //2. EnergyPlusの実行
        Calendar simulationDate = Calendar.getInstance();
        simulationDate.set(2006, evaluationMonth, evaluationDay, 1, 0);
        ControlEnergyPlus energyPlus = new ControlEnergyPlus(energyPlusConfigFile);
        double[][] resultData = energyPlus.simulate(temperature, simulationDate, simulationDate, idfDateOffset, idfTemperatureOffset);

        result = new Matrix(resultData);
    }

    /**
     * 一日のトータル消費電力量を算出する<br>
     * @return 全日消費電力量[J]
     */
    public double calculateTotalElectricEnergy()
    {
        Matrix allData = new Matrix(result);
        int[] rowsOfElectricEnergy = Cast.doubleToInt( new Vector(evaluationStartTimeForEnergy, 1,evaluationEndTimeForEnergy).get() );
        Matrix electricEnergyData = allData.getSubMatrix(rowsOfElectricEnergy, columnsOfElectricEnergy);
        return electricEnergyData.sum();	//室外機の総消費電力量[J]
    }
    /**
     * 一日のピーク消費電力量を算出する<br>
     * @return ピーク消費電力[kW]
     */
    public double calculatePeakElectricEnergy()
    {
        Matrix allData = new Matrix(result);
        int[] rowsOfElectricEnergy = Cast.doubleToInt( new Vector(evaluationStartTimeForEnergy, 1, evaluationEndTimeForEnergy).get() );
        Matrix electricEnergyData = allData.getSubMatrix(rowsOfElectricEnergy, columnsOfElectricEnergy);    // 電力データすべてを抽出
        Vector allEnergyData = electricEnergyData.sum(Matrix.DIRECTION_COLUMN); // 各時刻で全ての電力項目を足し合わせる
        return BuildingUtils.calculatePeakPower(allEnergyData, 1/timestepsPerHour);	// ピーク消費電力[kW]
    }

    /**
     * 1日の平均PMVを算出する
     * @return 1日の平均PMV値
     */
    public double calculateAveragePMV()
    {
        Matrix allData = new Matrix(result);
        int[] rowsOfPMV = Cast.doubleToInt( new Vector(evaluationStartTimeForComfortLevel, 1, evaluationEndTimeForComfortLevel).get() );
        Matrix pmvData = allData.getSubMatrix(rowsOfPMV, columnsOfPMV);
        return Math.abs(pmvData.mean());	//PMVの平均値の絶対値
    }

    /**
     * PMVの制約違反量を算出する
     * @return 制約違反量
     */
    public double countConstraintExceededTimesOfPMV()
    {
        Matrix allData = new Matrix(result);
        int[] rowsOfPMV = Cast.doubleToInt( new Vector(evaluationStartTimeForComfortLevel, 1, evaluationEndTimeForComfortLevel).get() );
        Matrix pmvData = allData.getSubMatrix(rowsOfPMV, columnsOfPMV);
        return pmvData.abs().round().sum();	//PMVが±0.5を超過した回数
    }

    /**
     * PMVの制約違反量を算出する
     * @return 制約違反量
     */
    public double countConstraintExceededTimesOfSetpointTemperature()
    {
        Matrix allData = new Matrix(result);
        int[] rowsOfTemperatureSetting = Cast.doubleToInt( new Vector(evaluationStartTimeForTemperatureSetting, 1, evaluationEndTimeForTemperatureSetting).get() );
        Matrix temperatureSettingData = allData.getSubMatrix(rowsOfTemperatureSetting, columnsOfTemperatureSetting);

        // 超過分をカウント
        Vector exceededCount = new Vector(temperatureSettingData.columnLength());
        // 温度設定の各列について
        for(int setting = 0; setting < temperatureSettingData.columnLength(); setting++) {
            // 時刻ごとに
            for (int time = 1; time < temperatureSettingData.length(); time++) {
                double temp = temperatureSettingData.get(time, setting) - temperatureSettingData.get(time-1, setting);  // 前回設定値と今回設定値の差分
                if( Math.abs(temp)>2.0 ){   // 差分が2を超過した場合超過分を積算
                    double increases = Math.abs(temp)-2.0;
                    exceededCount.set(setting, exceededCount.get(setting)+increases);
                }
            }
        }

        // 超過の総量を返す
        return exceededCount.sum();
    }

    /**
     * 設定温度スケジュールが±2℃の制約範囲に入るように修正する関数
     * @param temperature　設定温度
     * @return 制約を満たすように修正した設定温度
     */
    public static double[] limitTemperatureSettingSchedule(double[] temperature)
    {
        for (int i=1; i<temperature.length; i++) {
            // もし差分が±2以上なら2以内に収める
            double diff = temperature[i] - temperature[i-1];
            diff = Numeric.limit(diff, 2, -2);
            temperature[i] = temperature[i-1]+diff;
        }

        return temperature;
    }


    /**
     * 変数から設定温度スケジュールに変換する関数<br>
     * 最初の変数を最初の設定温度，残りの変数は前回の設定温度からの差分を表します．<br>
     * @param variable 変数配列
     * @param initialValue offset[時]までの設定温度
     * @param offset 1日の最初の設定時刻[時]
     * @param length 設定温度スケジュール配列の長さ
     * @return 設定温度スケジュール
     */
    public static double[] variableToTemperatureSettingUsingDifference(double[] variable, double initialValue, int offset, int length)
    {
        // initialValueで設定温度配列を作る
        Vector temperature = new Vector(length, initialValue);

        // 初期設定温度
        double firstValue = Numeric.round(variable[0]*12+18, 0.1);  // 初期設定温度 0～1*12+18->18～30に変換．0.1℃刻みで丸める．
        temperature.set(offset, Numeric.limit( firstValue,30,18) );	//

        // 残りの設定温度
        for(int v=1; v<variable.length; v++){
            double temp = Numeric.round(temperature.get( (offset+v)-1 ) + (variable[v]*4-2), 0.1);		//前回時刻温度±2℃の範囲で変化
            temperature.set( (offset+v), Numeric.limit(temp, 30, 18));
        }

        // 残りは最終値を保持
        if( (offset + variable.length) < length)
        {
            // 残りの設定温度
            for(int t=offset+variable.length; t<length; t++){
                double temp = temperature.get( t-1 ) ;		//前回時刻設定温度をそのまま次時刻に代入
                temperature.set( t, temp );
            }
        }

        return temperature.get();
    }

    /**
     * 変数から設定温度スケジュールに変換する関数<br>
     * 各変数を各時刻の温度に割り当てます．<br>
     * @param variable 変数配列
     * @param initialValue offset[時]までの設定温度
     * @param offset 1日の最初の設定時刻[時]
     * @param length 設定温度スケジュール配列の長さ
     * @return 設定温度スケジュール
     */
    public static double[] variableToTemperatureSettingUsingEachValue(double[] variable, double initialValue, int offset, int length)
    {
        // initialValueで設定温度配列を作る
        Vector temperature = new Vector(length, initialValue);

        // 設定温度
        for(int v=0; v<variable.length; v++){
            double temp = Numeric.round(variable[v]*12+18, 0.1);		//variableを0～1⇒18～30℃に変換．0.1℃刻みで丸める
            temperature.set(v+offset, Numeric.limit(temp, 30, 18));    // 18～30℃に制限する
        }

        // 残りは最終値を保持
        if( (offset + variable.length) < length)
        {
            // 残りの設定温度
            for(int t=offset+variable.length; t<length; t++){
                double temp = temperature.get( t-1 ) ;		//前回時刻設定温度をそのまま次時刻に代入
                temperature.set( t, temp );
            }
        }

        // 制約を満たすような修復をする
        double[] temperatureRepaired = limitTemperatureSettingSchedule(temperature.get());

        return temperatureRepaired;
    }

    /**
     * 変数から設定温度スケジュールに変換します<br>
     * 設定温度の差分を変数に変換します．<br>
     * @param temperature 設定温度
     * @param offset 設定温度の最初いくつ無視するか
     * @param length 設定温度のうちいくつを変数に変換するか
     * @return 変数配列
     */
    public static double[] temperatureSettingToVariableUsingDifference(double[] temperature, int offset, int length)
    {
        Vector temp = new Vector(temperature);
        // 長さ lengthの配列を用意
        Vector variable = new Vector(length);
        // 初期変数を計算
        variable.set(0, Numeric.limit((temp.get(offset)-18)/12, 1, 0));	//初期設定温度 (18~30-18)/12 -> 0~1
        // 残りの変数を計算
        for(int v=1; v<length; v++){
            double value = temp.get(v+offset) - temp.get(v+offset-1);
            variable.set(v, Numeric.limit((value+2)/4, 1, 0));				//前回時刻温度±2℃の範囲で変化
        }

        return variable.get();
    }

    /**
     * 変数から設定温度スケジュールに変換します<br>
     * 各時刻の設定温度をそのまま変数に変換します<br>
     * @param temperature 設定温度
     * @param offset 設定温度の最初いくつ無視するか
     * @param length 設定温度のうちいくつを変数に変換するか
     * @return 変数配列
     */
    public static double[] temperatureSettingToVariableUsingEachValue(double[] temperature, int offset, int length)
    {
        // 長さ lengthの配列を用意
        Vector variable = new Vector(length);
        // 変数を計算
        for(int v=0; v<length; v++){
            double temp = temperature[v+offset];
            variable.set(v, Numeric.limit((temp+2)/4, 1, 0));				// 設定温度をそのまま変数0～1に変換
        }

        return variable.get();
    }
}
