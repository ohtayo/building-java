package jp.ohtayo.building.environment;

import java.lang.Math;

/**
 * 温冷感予測値であるPMV(Predicted Mean Vote)を計算するクラスです。<br>
 * 部屋の代表点の温度・湿度・風速・放射温度と材質者の着衣量・代謝量から、<br>
 * 温冷感予測値を計算します。温冷感予測値と温冷感は以下の様な関係です。<br>
 * 寒い -3 ... -2 ... -1 ... 0 ... +1 ... +2 ... +3 暑い<br>
 * <br>
 * また、あるPMV値の時の不満足者率を示すPPD(Predicted Percentage of Dissatisfied)を計算するメソッドを提供します。<br>
 * PMV,PPDの詳細についてはFanger教授の論文を参照してください。<br>
 *
 * @author ohtayo <ohta.yoshihiro@outlook.jp>
*/
public class PMV {
	
	/** 被覆表面温度の収束精度*/
	private static final double EPS = 0.00001;
	
	/** 被覆表面温度繰り返し計算の最大繰り返し回数*/
	private static final double MAX_ITERATE = 1000;

	/** 外部仕事のMET値のデフォルト値[MET] */
	private static final double DEFAULT_EXTERNAL_WORK = 0.0;

	/** PMV value */
	private double pmv;

	/**
	 * Constructor
	 * @param Ta temperature[C]
	 * @param Rh relative humidity[%]
	 * @param Va air velocity[m/s]
	 * @param Tr mean radiation temperature[C]
	 * @param Icl clo unit[clo]
	 * @param M metabolic ratio[met]
	 */
	public PMV(double Ta, double Rh, double Va, double Tr, double Icl, double M)
	{
		calculate(Ta, Rh, Va, Tr, Icl, M, PMV.DEFAULT_EXTERNAL_WORK);
	}

	/**
	 * Constructor
	 * @param Ta temperature[C]
	 * @param Rh relative humidity[%]
	 * @param Va air velocity[m/s]
	 * @param Tr mean radiation temperature[C]
	 * @param Icl clo unit[clo]
	 * @param M metabolic ratio[met]
	 * @param W external work[W/m^2]
	 */
	public PMV(double Ta,double Rh,double Va,double Tr,double Icl,double M,double W)
	{
		calculate(Ta, Rh, Va, Tr, Icl, M, W);
	}

	/**
	 * getter
	 * @return PMV value
	 */
	public double get(){ return pmv; }

	/** PMV値を計算します。<br>
	 * @param Ta 温度[℃]
	 * @param Rh 相対湿度[%]
	 * @param Va 風速[m/s]
	 * @param Tr 平均放射温度[℃]
	 * @param Icl 着衣量[clo]
	 * @param M 代謝量[met]
	 * @param W 外部仕事[W/m^2]
	 * @return PMV値(double型) エラーの場合NaNを返します。
	 */
	public void calculate(double Ta,double Rh,double Va,double Tr,double Icl,double M,double W)
	{
		//変数の定義
		double Pk, Pb, Pc, Pa;
		double Fcl, Tcl, Tcl_, Hc, Hc1, Hc2;
		double Ed, Es, Ere, Cre, R, C, L;
		double value;

		//入力チェック
		int error = inputErrorCheck(Ta, Rh, Va, Tr, Icl, M, W);
		if ( error != 0 ){
			pmv = Double.NaN;
		}
		
		//MET→W/m2に変換
		M = M *58.2;
		
		//①水蒸気分圧Paを計算する
		Pk = (673.4 -(1.8 *Ta));
		Pc = 3.2437814 + 0.00326014 * Pk  + 2.00658 *0.000000001 *Pk *Pk *Pk;
		Pb = (1165.09 - Pk) * (1 + 0.00121547 *Pk);
		Pa = (Rh /100 *22105.8416) / Math.exp(2.302585 *Pk *Pc /Pb) *1000.0;

		//②着衣表面積/裸体表面積の比を計算
		if ( Icl > 0.5 ){
			Fcl = 1.05 + 0.1 *Icl;
		}else{
			Fcl = 1 + 0.2 *Icl;
		}
		
		//③衣服表面温度を計算(収束するまでループ)
		Tcl = Ta;	//衣服表面温度の初期値は室温
		Tcl_ = Tcl;	//Tclの過去値保存
		int count = 0;
		for(;;)
		{
			//Tcl_値を更新
			Tcl_ = Tcl_ *0.8 + Tcl *0.2;
			//対流熱伝導率を計算
			Hc1 = 2.38 *Math.sqrt( Math.sqrt(Math.abs(Tcl - Ta)) );
			Hc2 = 12.1 *Math.sqrt(Va);
			if ( Hc1 > Hc2 ){
				Hc = Hc1;
			}else{
				Hc = Hc2;
			}

			//衣服表面温度を更新
		    Tcl = 35.7 - 0.028 *(M - W) -0.155 *Icl *( calcR(Fcl, Tcl_, Tr) + Fcl *Hc *(Tcl_ - Ta) );
			
			//収束判定
			if ( Math.abs(Tcl - Tcl_) < EPS ){
				break;
			}
			//Tclが発散して不定値になったらエラー
			if ( java.lang.Double.isNaN(Tcl) ){
				pmv = Double.NaN;
			}
			//ループ回数が多過ぎたらエラー
			if ( count++ > MAX_ITERATE ){
				pmv = Double.NaN;
			}
		}
				
		//④人体熱負荷を求める
		Ed = 3.05 *0.001 *(5733.0 - 6.99 *(M - W) - Pa);//不感蒸泄量(発汗以外による蒸汗熱損失量)
		Es = 0.42 *((M - W) - 58.15);					//蒸発熱損失量(発汗による熱損失量)
		Ere = 1.73 *0.00001 *M *(5867.0 - Pa);			//呼吸潜熱損失量
		Cre = 0.0014 *M *(34.0 - Ta);					//顕熱損失量
		C = Fcl *Hc *(Tcl - Ta);						//対流熱損失量
		R = calcR(Fcl, Tcl, Tr);						//放射熱損失量
		
		//人体熱負荷
		L = (M - W) - Ed - Es - Ere - Cre - R - C;

		//⑤PMV値
		value = L *(0.303 *Math.exp(-0.036 *M) + 0.028 );
		
		//PMV値がリミットから外れたらエラー
		if ( ( value > 5 ) || ( value < -5 ) ){
			pmv = Double.NaN;
		}

		pmv = value;
	}
	
	/** 予測不満足者率PPDを計算します。<br>
	 * @return PPD値
	 */
	public double getPPD()
	{
		return 100 - 95 * Math.exp( -1*(0.03353*pmv*pmv*pmv*pmv + 0.2179*pmv*pmv) );
	}
	
	/** 放射熱損失量を計算します。<br>
	 * @param Fcl 着衣表面積/裸体表面積の比
	 * @param Tcl 衣服表面温度[℃]
	 * @param Tr 放射温度[℃]
	 * @return 放射熱損失量
	 */
	private double calcR(double Fcl, double Tcl, double Tr)
	{
		return 3.96 * 0.00000001 *Fcl *( Math.pow((Tcl+273.15),4)-Math.pow((Tr+273.15), 4) );
	}
	
	/** PMV値を計算の入力エラーチェックをします。<br>
	 * @param Ta 温度[℃]
	 * @param Rh 相対湿度[%]
	 * @param Va 風速[m/s]
	 * @param Tr 放射温度[℃]
	 * @param Icl 着衣量[clo]
	 * @param M 代謝量[met]
	 * @return エラーコード(0:正常 -1:相対湿度異常 -2:風速異常 -3:着衣量異常 -4:代謝量異常 -5:外部仕事異常)
	 */
	private int inputErrorCheck(double Ta,double Rh,double Va,double Tr,double Icl,double M,double W)
	{
		//相対湿度は負にならない
		if (Rh < 0.0)
		{
			return -1;
		}
		//風速は負にならない
		else if (Va < 0.0)
		{
			return -2;
		}
		//着衣量は負にならない
		else if (Icl < 0.0)
		{
			return -3;
		}
		//代謝量は負にならない
		else if (M < 0.0)
		{
			return -4;
		}

		return 0;
	}

}
