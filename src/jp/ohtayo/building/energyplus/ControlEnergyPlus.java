package jp.ohtayo.building.energyplus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import jp.ohtayo.commons.io.Csv;
import jp.ohtayo.commons.io.Text;
import jp.ohtayo.commons.util.Cast;
import jp.ohtayo.commons.log.Logging;
import jp.ohtayo.commons.math.Matrix;
import jp.ohtayo.commons.math.Vector;
import jp.ohtayo.commons.io.TimeSeries;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * Control class for EnergyPlus building energy simulator.
 *
 * @author ohtayo (ohta.yoshihiro@outlook.jp)
 */
public class ControlEnergyPlus {

	/** ターゲットフォルダ・ファイルのロケーション定義 */
	private String exeFolder;
	private final static String exeFile = "energyplus.exe";
	private String weatherFolder;
	private String weatherFile;
  private String idfFolder;
  private String idfBaseFolder;
	private String idfFile;
	private String sqliteFile;
	private String csvFile;

	/**
	 * constructor.
	 * @param configFileName name of ConfigEnergyPlus configuration file
	 */
	public ControlEnergyPlus(String configFileName)
	{
		readConfig(configFileName);
	}

	/**
	 * コンフィグファイル(xml)からEnergyPlusの実行環境情報を取得して格納する．
	 * @param configFileName コンフィグファイル(xml)の絶対パス
	 */
	public void readConfig(String configFileName){
		// コンフィグの読み込み
		ConfigEnergyPlus config = new ConfigEnergyPlus();
		if(	!config.read(configFileName) )	{
			Logging.logger.severe("\nプログラムを終了します。");
			return;
		}

		// 読込結果の格納
		exeFolder = config.exeFolder;
		idfBaseFolder = config.idfBaseFolder;
		String threadName = Thread.currentThread().getName();
		idfFolder = idfBaseFolder + threadName+"\\";
		idfFile = config.idfFile;
		weatherFile = config.weatherFile;
		weatherFolder = exeFolder+"WeatherData\\";
		String idfName = FilenameUtils.removeExtension(idfFile);
		sqliteFile = "eplusout.sql";
		csvFile = "eplusout.csv";

		Logging.logger.info("exe = " + exeFolder+exeFile);
		Logging.logger.info("idf= " + idfFolder+idfFile);
		Logging.logger.info("csv= " + idfFolder+csvFile);
		Logging.logger.info("sql= " + idfFolder+sqliteFile);
		Logging.logger.info("weatherFile = " + weatherFolder+weatherFile);
	}

	/**
	 * EnergyPlusの実行プログラム
	 * @return energyPlusの実行結果(0なら正常終了、1ならエラー)
	 */
	public int executeEnergyPlus()
	{
		//コマンドの作成
		String command = exeFolder + exeFile +
						 " -w " + weatherFolder + weatherFile +
						 " -d " + idfFolder +
						 " -r " + idfFolder + idfFile;
		System.out.println(command);
		//EnergyPlusプログラム実行
		int ret = 0;
		Runtime runtime = Runtime.getRuntime();
		try{
			Process process = runtime.exec(command);
			ret = process.waitFor();
			System.out.println(ret);
			if(ret!=0)	Logging.logger.severe("EnergyPlus occurred error(s).");
		}catch(Exception e){
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * 温度配列からidfファイルの設定温度を変更して、EnergyPlusで消費電力・空調能力・温湿度を計算します<br>
	 * @param settemp 設定温度[℃]の配列
	 * @param startDay シミュレーション開始日
	 * @param endDay シミュレーション終了日
	 * @param idfDateOffset IDFファイルのうち日付指定箇所の行番号
	 * @param idfTemperatureOffset IDFファイルのうち温度指定箇所の行番号
	 * @return EnergyPlusの算出した全データ
	 */
	public double[][] simulate(double[] settemp, Calendar startDay, Calendar endDay, int idfDateOffset, int idfTemperatureOffset)
	{
		// フォルダとIDFファイルが有るか確認．無ければフォルダを作ってidfをコピーする
		if( Files.notExists(Paths.get(idfFolder)) || Files.notExists(Paths.get(idfFolder+idfFile))){
			String sourceFolder = idfBaseFolder + "0\\";	//フォルダ名定義
			System.out.println("copy from: "+sourceFolder+" \ncopy to  : "+idfFolder);
			try {
				FileUtils.copyDirectory(new File(sourceFolder), new File(idfFolder));
			}catch(IOException e){
				Logging.logger.severe(e.getMessage());
				e.printStackTrace();
			}
		}

		//1. variableの設定温度組合せへの変換
		Vector temperature = new Vector(settemp);

		//2. idfファイル書き換え
		String[] idf = new Text().read(idfFolder+idfFile).getStringArray();
		//2.1 日付の書き換え
		idf[idfDateOffset+0] = "    " + String.valueOf(startDay.get(Calendar.MONTH)) + ",                      !- Begin Month\r";
		idf[idfDateOffset+1] = "    " + String.valueOf(startDay.get(Calendar.DATE))  + ",                      !- Begin Day of Month\r";
		idf[idfDateOffset+2] = "    " + String.valueOf(endDay.get(Calendar.MONTH) ) + ",                      !- End Month\r";
		idf[idfDateOffset+3] = "    " + String.valueOf(endDay.get(Calendar.DATE)  ) + ",                      !- End Day of Month\r";

		//2.2 設定温度情報書き換え
		for(int i=0; i<temperature.length()-1; i++){
			idf[idfTemperatureOffset+i*2] = "    "+String.valueOf(temperature.get( i))+",                    !- Value Until Time" + String.valueOf(i+1)+"\r";
		}//最終行だけセミコロン
		idf[idfTemperatureOffset+(temperature.length()-1)*2] = "    "+String.valueOf(temperature.get(temperature.length()-1))+";                    !- Value Until Time" + String.valueOf(temperature.length())+"\r";

		Text text = new Text();
		text.set(idf);
		text.write(idfFolder+idfFile);	//utf-8, BOM無し

		//3. EnergyPlusプログラム実行
		executeEnergyPlus();

		//4. 出力のCSVデータをまとめる
		//CSVデータは0列目：日時，1列目外気温，2列目外気湿度，3～389列目：各部屋温度・湿度・PMV，390列目：冷房能力，391列目：消費電力，のならび
		String filename = idfFolder + csvFile;
//		Matrix csvData = new Matrix(Csv.read(filename, 1, 1));
		TimeSeries csvData = new TimeSeries(filename, 1, " MM/dd  HH:mm:ss");
		int[] temp1index = Cast.doubleToInt(Csv.read(idfFolder + "tempgroundindex_ep.csv")[0]);
		int[] temp2index = Cast.doubleToInt(Csv.read(idfFolder + "tempmiddleindex_ep.csv")[0]);
		int[] temp3index = Cast.doubleToInt(Csv.read(idfFolder + "temptopindex_ep.csv")[0]);
		int[] humi1index = Cast.doubleToInt(Csv.read(idfFolder + "humigroundindex_ep.csv")[0]);
		int[] humi2index = Cast.doubleToInt(Csv.read(idfFolder + "humimiddleindex_ep.csv")[0]);
		int[] humi3index = Cast.doubleToInt(Csv.read(idfFolder + "humitopindex_ep.csv")[0]);
		int[] pmv1index = Cast.doubleToInt(Csv.read(idfFolder + "pmvgroundindex_ep.csv")[0]);
		int[] pmv2index = Cast.doubleToInt(Csv.read(idfFolder + "pmvmiddleindex_ep.csv")[0]);
		int[] pmv3index = Cast.doubleToInt(Csv.read(idfFolder + "pmvtopindex_ep.csv")[0]);
		Vector temp1 = csvData.getColumns(temp1index).mean("row");
		Vector temp2 = csvData.getColumns(temp2index).mean("row");
		Vector temp3 = csvData.getColumns(temp3index).mean("row");
		Vector humi1 = csvData.getColumns(humi1index).mean("row");
		Vector humi2 = csvData.getColumns(humi2index).mean("row");
		Vector humi3 = csvData.getColumns(humi3index).mean("row");
		Vector pmv1 = csvData.getColumns(pmv1index).mean("row");
		Vector pmv2 = csvData.getColumns(pmv2index).mean("row");
		Vector pmv3 = csvData.getColumns(pmv3index).mean("row");

		int[] energyIndex = {csvData.columnLength()-1, csvData.columnLength()-2};	//消費電力，空調能力
		int[] outdoorIndex = {0, 1, 2};	//時刻，設定温度，外気温，外気湿度
		Matrix energy = csvData.getColumns(energyIndex);
		Matrix outdoor = csvData.getColumns(outdoorIndex);

		// 設定温度の追加
		Vector settempdata = new Vector(5, temperature.get(0));
		for(int t=1; t<temperature.length()-1; t++) {
			Vector temp = new Vector(6, temperature.get(t));
			settempdata = settempdata.add(temp);
		}
		settempdata = settempdata.add(new Vector(1, temperature.get(temperature.length()-1)));

		//年と分の補正 必要性に応じて後で追加する．
		//outdoor.set(0, 0, outdoor.get(0, 0)-600);//時刻の先頭を10分前に戻す
		//outdoor.setColumn(0, outdoor.getColumn(0).plus( ((2006-1970)*365+9)*24*60*60 ));	//36年と8日分足す

		Matrix alldata = new Matrix(csvData.length(), 15);
		alldata.setSubMatrix(0, csvData.length(), 0, outdoor.columnLength(), outdoor);	//時刻，設定温度，外気温，外気湿度
		alldata.setColumn(3, settempdata);
		alldata.setColumn(4, temp1);
		alldata.setColumn(5, humi1);
		alldata.setColumn(6, temp2);
		alldata.setColumn(7, humi2);
		alldata.setColumn(8, temp3);
		alldata.setColumn(9, humi3);
		alldata.setColumn(10, pmv1);
		alldata.setColumn(11, pmv2);
		alldata.setColumn(12, pmv3);
		alldata.setSubMatrix(0, csvData.length(), 13, energy.columnLength(), energy);

		// 5. 抽出したデータの書き出し
		String header = "time, outdoortemp, outdoorhumi, settemp, groundtemp, groundhumi, middletem, middlehumi, toptemp, tophumi, groundpmv, middlepmv, toppmv, electricenergy, coolingenergy";
		new TimeSeries(alldata).write(idfFolder+"eplusout_picup.csv", header);

		// 10. EnergyPlusの作業フォルダを削除
		System.out.println("delete "+idfFolder);
		try{
			FileUtils.deleteDirectory(new File(idfFolder));
		}catch(IOException e){
			Logging.logger.severe(e.getMessage());
			e.printStackTrace();
		}

		return alldata.get();
	}


	/**
	 * epwファイルの所定の時刻の気温、湿度を書き換える関数<br>
	 * 本関数の呼び出し前にepwファイルをバックアップしておくこと<br>
	 * @param currentTime 2006/8/21 00:00:00から現在までの秒数
	 * @param temperature その時刻の実際の気温
	 * @param humidity その時刻の実際の湿度
	 */
	public void rewriteEPWFile( int currentTime, double temperature, double humidity) {
		//シミュレーション年月日を指定
		int year = 2006;
		int month = 8;
		int dayOfMonth = 21;
		// epwを読み込み
		String[] epw = new Text().read(weatherFile).getStringArray();

		// 時刻から書き換え行を算出
		Calendar calBase = Calendar.getInstance();
		calBase.set(year, 1, 1, 1, 0);	//2006年1月1日1時0分に時刻をセット
		Calendar cal = Calendar.getInstance();
		cal.set(year, month, dayOfMonth, Math.round(currentTime/3600), Math.round(currentTime/60));
		int difference = (int)((cal.getTimeInMillis() - calBase.getTimeInMillis()) / (1000 * 60 * 60));	//現在時刻と1/1の時間差
		int index = difference + (9-1);	//9行目が1月1日の1時0分なので、足して日時の行番号を作る
		index -= 1;	//配列のindexは0スタートなので行数を-1する

		// データの書き換え
		String[] temp = epw[index].split(",",-1);
		String line = "";
		for(int i=0; i<6; i++) {
			line += temp[i] + ",";
		}
		line += String.valueOf(temperature) + "," +temp[7]+ "," + String.valueOf((int)humidity)+",";	//外気温・露点温度・相対湿度
		for(int i=9; i<temp.length-1; i++) {
			line += temp[i] + ",";
		}
		line+=temp[temp.length-1];	//最後の1津のデータの後は","は不要
		epw[index] = line;

		// epwの書き込み
		new Text(epw).write(weatherFile);
	}

	/**
	 * epwファイルのある1日の外気温を別の1日の外気温に書き換える<br>
	 * 本関数の呼び出し前にepwファイルをバックアップしておくこと<br>
	 * @param base 書き換え元の気温のある日
	 * @param target 書き換え先の日
	 */
	public void rewriteEPWFile( Calendar base, Calendar target) {
		String epwFile = weatherFolder+weatherFile;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		System.out.println(sdf.format(base.getTime())+"の気温を"+sdf.format(target.getTime())+"に入れ替えます．");

		//シミュレーション年月日を指定
		int year = 2006;
		//書き換え行数を指定
		int hourOfDay = 25;

		// epwを読み込み
		String[] epw = new Text().read(epwFile).getStringArray();

		// 時刻から書き換え行を算出
		Calendar cal11 = Calendar.getInstance();
		cal11.set(year, 1, 1, 1, 0);	//2006年1月1日1時0分に時刻をセット
		int idxBase = (int)((base.getTimeInMillis() - cal11.getTimeInMillis()) / (1000 * 60 * 60))+(9-1)-1;	//現在時刻と1/1の時間差とヘッダ行数
		int idxTarget = (int)((target.getTimeInMillis() - cal11.getTimeInMillis()) / (1000 * 60 * 60))+(9-1)-1;

		// データの書き換え
		for(int l=0; l<=hourOfDay; l++) {
			//元データの読み込み
			String[] lineBase = epw[idxBase+l].split(",",-1);
			String[] lineTarget = epw[idxTarget+l].split(",",-1);
			lineTarget[6] = lineBase[6];	// 6列目が外気温
			String line = "";
			for(int i=0; i<lineTarget.length-1; i++) {
				line += lineTarget[i] + ",";
			}
			line+=lineTarget[lineTarget.length-1];	//最後の1つのデータの後は","は不要
			epw[idxTarget+l] = line;
		}
		// epwの書き込み
		new Text(epw).write(epwFile);
	}

}
