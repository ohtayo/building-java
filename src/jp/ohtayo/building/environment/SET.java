package jp.ohtayo.building.environment;

import jp.ohtayo.commons.util.Cast;

/**
 * 新標準有効温度(SET*)を算出するクラスです。<br>
 * 部屋の代表点の温度・湿度・風速・放射温度と材質者の着衣量・代謝量から、<br>
 * 標準環境における有効温度を計算します。<br>
 *
 * 参照：ANSI/ASHRAE Standard 55-2013
 * Thermal Environmental Conditions for Human Occupancy
 *
 * @author ohtayo <ohta.yoshihiro@outlook.jp>
 */
public class SET {

	/** 外部仕事のMET値のデフォルト値[MET] */
	private static final double DEFAULT_EXTERNAL_WORK = 0.0;

	/** 大気圧のデフォルト値[kPa] */
	private static final double DEFAULT_ATMOSPHERIC_PRESSURE = 101.325;

	/**
	 * SET value
	 */
	private double set;

	/**
	 * constructor
	 * SET*を、外部仕事・大気圧をデフォルト値で計算させる。
	 * @param TA 温度[℃]
	 * @param RH 相対湿度[%]
	 * @param VEL 風速[m/s]
	 * @param TR 平均放射温度[℃]
	 * @param CLO 着衣量[clo]
	 * @param MET 代謝量[met]
	 */
	public SET(double TA,double RH,double VEL,double TR,double CLO,double MET)
	{
		calculate(TA, RH, VEL, TR, CLO, MET, DEFAULT_EXTERNAL_WORK, DEFAULT_ATMOSPHERIC_PRESSURE);
	}

	/**
	 * constructor
	 * SET*を、外部仕事・大気圧をデフォルト値で計算させる。
	 * @param TA 温度[℃]
	 * @param RH 相対湿度[%]
	 * @param VEL 風速[m/s]
	 * @param TR 平均放射温度[℃]
	 * @param CLO 着衣量[clo]
	 * @param MET 代謝量[met]
	 * @param WME 外部仕事[W/m2] = 0
	 * @param PATM 気圧[kPa]
	 */
	public SET(double TA,double RH,double VEL,double TR,double CLO,double MET,double WME, double PATM)
	{
		calculate(TA, RH, VEL, TR, CLO, MET, WME, PATM);
	}

	/**
	 * getter
	 * @return SET value
	 */
	public double get(){ return set; }

	/** SET*を計算します。<br>
	 * @param TA 温度[℃]
	 * @param RH 相対湿度[%]
	 * @param VEL 風速[m/s]
	 * @param TR 平均放射温度[℃]
	 * @param CLO 着衣量[clo]
	 * @param MET 代謝量[met]
	 * @param WME 外部仕事[W/m2] = 0
	 * @param PATM 気圧[kPa]
	 */
	public void calculate(double TA,double RH,double VEL,double TR,double CLO,double MET,double WME, double PATM)
	{
		//入力チェック
		int error = inputErrorCheck(TA, RH, VEL, TR, CLO, MET, WME, PATM);
		if ( error != 0 ){
			set = Double.NaN;
		}

		//Input variables -- TA (air temperature): °C, TR (mean radiant temperature): °C, VEL (air velocity): m/s,
		//RH (relative humidity): %, MET: met unit, CLO: clo unit, WME (external work): W/m^2, PATM (atmospheric pressure): kPa
		double KCLO = 0.25;
		double BODYWEIGHT = 69.9; //kg
		double BODYSURFACEAREA = 1.8258; //m^2
		double METFACTOR = 58.2; //W/m^2
		double SBC = 0.000000056697; //Stefan-Boltzmann constant (W/m^2K4)
		double CSW = 170.0;
		double CDIL = 120.0;
		double CSTR = 0.5;
		double LTIME = 60.0;
		double VaporPressure = RH * FindSaturatedVaporPressureTorr(TA)/100.0;
		double AirVelocity = Math.max(VEL, 0.1);
		double TempSkinNeutral = 33.7;
		double TempCoreNeutral = 36.49;
		double TempBodyNeutral = 36.49;
		double SkinBloodFlowNeutral = 6.3;
		double TempSkin = TempSkinNeutral; //Initial values
		double TempCore = TempCoreNeutral;
		double SkinBloodFlow = SkinBloodFlowNeutral;
		double MSHIV = 0.0;
		double ALFA = 0.1;
		double ESK = 0.1 * MET;
		double PressureInAtmospheres = PATM * 0.009869;
		double RCL = 0.155 * CLO;
		double FACL = 1.0 + 0.15 * CLO;
		double LR = 2.2/PressureInAtmospheres; //Lewis Relation is 2.2 at sea level
		double RM = MET * METFACTOR;
		double M = MET * METFACTOR;
		double ICL, WCRIT;
		if (CLO <= 0) {
			WCRIT = 0.38 * Math.pow(AirVelocity, -0.29);
			ICL = 1.0;
		} else {
			WCRIT = 0.59 * Math.pow(AirVelocity, -0.08);
			ICL = 0.45;
		}

		double CHC = 3.0 * Math.pow(PressureInAtmospheres, 0.53);
		double CHCV = 8.600001 * Math.pow((AirVelocity * PressureInAtmospheres), 0.53);
		CHC = Math.max(CHC, CHCV);
		double CHR = 4.7;
		double CTC = CHR + CHC;
		double RA = 1.0/(FACL * CTC); //Resistance of air layer to dry heat transfer
		double TOP = (CHR * TR + CHC * TA)/CTC;
		double TCL = TOP + (TempSkin - TOP)/(CTC * (RA + RCL));
		double TCL_OLD = TCL;
		boolean flag = true;
		double DRY, HFCS, ERES, CRES, SCR, SSK, TCSK, TCCR, DTSK, DTCR, TB, SKSIG,WARMS, COLDS, CRSIG,WARMC, COLDC, BDSIG, WARMB, REGSW, ERSW, REA, RECL, EMAX, PRSW, PWET, EDIF;
		DRY=0.0;	EMAX = 0.0;	PWET = 0.0;	//for文中で初期化される。
		for (double TIM = 1; TIM <= LTIME; TIM++) { //Begin for iteration
			do {
				if (flag) {
					TCL_OLD = TCL;
					CHR = 4.0 * SBC * Math.pow(((TCL + TR)/2.0 + 273.15), 3.0) * 0.72;
					CTC = CHR + CHC;
					RA = 1.0/(FACL * CTC); //Resistance of air layer to dry heat transfer
					TOP = (CHR * TR + CHC * TA)/CTC;
				}
				TCL = (RA * TempSkin + RCL * TOP)/(RA + RCL);
				flag = true;
			} while (Math.abs(TCL - TCL_OLD) > 0.01);
			flag = false;

			DRY = (TempSkin - TOP)/(RA + RCL);
			HFCS = (TempCore - TempSkin) * (5.28 + 1.163 * SkinBloodFlow);
			ERES = 0.0023 * M * (44.0 -VaporPressure);
			CRES = 0.0014 * M * (34.0 - TA);
			SCR = M - HFCS - ERES - CRES - WME;
			SSK = HFCS - DRY - ESK;
			TCSK = 0.97 * ALFA * BODYWEIGHT;
			TCCR = 0.97 * (1 - ALFA) * BODYWEIGHT;
			DTSK = (SSK * BODYSURFACEAREA)/(TCSK * 60.0); //°C/min
			DTCR = SCR * BODYSURFACEAREA/(TCCR * 60.0); //°C/min
			TempSkin = TempSkin + DTSK;
			TempCore = TempCore + DTCR;
			TB =ALFA * TempSkin + (1 - ALFA) * TempCore;
			SKSIG = TempSkin - TempSkinNeutral;
			WARMS = (double)Cast.booleanToInt(SKSIG > 0) * SKSIG;
			COLDS = (double)Cast.booleanToInt((-1.0 * SKSIG) > 0) * (-1.0 * SKSIG);
			CRSIG = (TempCore - TempCoreNeutral);
			WARMC = (double)Cast.booleanToInt(CRSIG > 0) * CRSIG;
			COLDC = (double)Cast.booleanToInt((-1.0 * CRSIG) > 0) * (-1.0 * CRSIG);
			BDSIG = TB - TempBodyNeutral;
			WARMB = (double)Cast.booleanToInt(BDSIG > 0) * BDSIG;
			SkinBloodFlow = (SkinBloodFlowNeutral + CDIL * WARMC)/(1 + CSTR * COLDS);
			SkinBloodFlow = Math.max(0.5, Math.min(90.0, SkinBloodFlow));
			REGSW = CSW * WARMB * Math.exp(WARMS/10.7);
			REGSW = Math.min(REGSW, 500.0);

			ERSW = 0.68 * REGSW;
			REA = 1.0/(LR * FACL * CHC); //Evaporative resistance of air layer
			RECL = RCL/(LR * ICL); //Evaporative resistance of clothing (icl=.45)
			EMAX = (FindSaturatedVaporPressureTorr(TempSkin) -VaporPressure)/(REA + RECL);
			PRSW = ERSW/EMAX;
			PWET = 0.06 + 0.94 * PRSW;
			EDIF = PWET * EMAX - ERSW;
			ESK = ERSW + EDIF;
			if (PWET > WCRIT) {
				PWET = WCRIT;
				PRSW = WCRIT/0.94;
				ERSW = PRSW * EMAX;
				EDIF = 0.06 * (1.0 - PRSW) * EMAX;
				ESK = ERSW + EDIF;
			}
			if (EMAX < 0) {
				EDIF = 0;
				ERSW = 0;
				PWET = WCRIT;
				PRSW = WCRIT;
				ESK = EMAX;
			}
			ESK = ERSW + EDIF;
			MSHIV = 19.4 * COLDS * COLDC;
			M = RM + MSHIV;
			ALFA = 0.0417737 + 0.7451833/(SkinBloodFlow + 0.585417);
		} //End for iteration

		double HSK = DRY + ESK; //Total heat loss from skin
		double RN = M - WME; //Net metabolic heat production
		double ECOMF = 0.42 * (RN - (1 * METFACTOR));
		if (ECOMF < 0.0) ECOMF = 0.0; //From Fanger
		EMAX = EMAX * WCRIT;
		double W = PWET;
		double PSSK = FindSaturatedVaporPressureTorr(TempSkin);
		double CHRS = CHR; //Definition of ASHRAE standard environment denoted “S”
		double CHCS;
		if (MET < 0.85) {
			CHCS = 3.0;
		} else {
			CHCS = 5.66 * Math.pow(((MET - 0.85)), 0.39);
			CHCS = Math.max(CHCS, 3.0);
		}

		double CTCS = CHCS + CHRS;
		double RCLOS = 1.52/((MET - WME/METFACTOR) + 0.6944) - 0.1835;
		double RCLS = 0.155 * RCLOS;
		double FACLS = 1.0 + KCLO * RCLOS;
		double FCLS = 1.0/(1.0 + 0.155 * FACLS * CTCS * RCLOS);
		double IMS = 0.45;
		double ICLS = IMS * CHCS/CTCS * (1 - FCLS)/(CHCS/CTCS - FCLS * IMS);
		double RAS = 1.0/(FACLS * CTCS);
		double REAS = 1.0/(LR * FACLS * CHCS);
		double RECLS = RCLS/(LR * ICLS);
		double HD_S = 1.0/(RAS + RCLS);
		double HE_S = 1.0/(REAS + RECLS);

		//SET determined using Newton’s iterative solution
		double DELTA = 0.0001;
		double dx = 100.0;
		double SET=0.0, ERR1, ERR2;
		double SET_OLD = TempSkin - HSK/HD_S; //Lower bound for SET
		while (Math.abs(dx) > .01) {
			ERR1 = (HSK - HD_S * (TempSkin - SET_OLD) - W * HE_S * (PSSK - 0.5 * FindSaturatedVaporPressureTorr(SET_OLD)));
			ERR2 = (HSK - HD_S * (TempSkin - (SET_OLD + DELTA)) - W * HE_S * (PSSK - 0.5 * FindSaturatedVaporPressureTorr((SET_OLD + DELTA))));
			SET = SET_OLD - DELTA * ERR1/(ERR2 - ERR1);
			dx = SET - SET_OLD;
			SET_OLD = SET;
		}

		set = SET;
	}

	/**
	 * Helper function for pierceSET calculates SaturatedVapor Pressure (Torr) at Temperature T (°C)
	 * @param T
	 */
	private double FindSaturatedVaporPressureTorr(double T)
	{
		return Math.exp(18.6686 - 4030.183/(T + 235.0));
	}

	/** SET*値を計算の入力エラーチェックをします。<br>
	 * @param Ta 温度[℃]
	 * @param Rh 相対湿度[%]
	 * @param Va 風速[m/s]
	 * @param Tr 放射温度[℃]
	 * @param Icl 着衣量[clo]
	 * @param M 代謝量[met]
	 * @param W 外部仕事[W/m2]
	 * @param P 大気圧[kPa]
	 * @return エラーコード(0:正常 -1:相対湿度異常 -2:風速異常 -3:着衣量異常 -4:代謝量異常 -5:外部仕事異常)
	 */
	private static int inputErrorCheck(double Ta,double Rh,double Va,double Tr,double Icl,double M,double W, double P)
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
