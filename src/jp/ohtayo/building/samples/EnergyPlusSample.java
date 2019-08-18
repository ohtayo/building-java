package jp.ohtayo.building.samples;

import jp.ohtayo.building.energyplus.ControlEnergyPlus;
import jp.ohtayo.commons.io.Csv;
import jp.ohtayo.commons.io.TimeSeries;
import jp.ohtayo.commons.math.Matrix;
import jp.ohtayo.commons.math.Vector;
import jp.ohtayo.commons.util.Cast;

import java.util.Calendar;

public class EnergyPlusSample {
    public static void main(String[] args){
        Matrix temperature = new Matrix(Csv.read("./in.csv",1,0));
        String energyPlusConfigFile = "./xml/energyplus.xml";
        Calendar simulationDate = Calendar.getInstance();
        simulationDate.set(2006, 8, 21, 1, 0);
        ControlEnergyPlus energyPlus = new ControlEnergyPlus(energyPlusConfigFile);
        int idfDateOffset = 154 -1;			//idfファイルの最初の日付の行数-1
        int idfTemperatureOffset = 429 -1;	//idfファイルの最初の温度の行数-1
        double[][] resultData = energyPlus.simulate(temperature.getColumn(0).get(), simulationDate, simulationDate, idfDateOffset, idfTemperatureOffset);

        // resultDataの間引き
        Vector temp = new Vector(0, 0, 0).add(new Vector(5, 6, 143));
        int[] index = Cast.doubleToInt(temp.get());
        TimeSeries data = new TimeSeries(new TimeSeries(resultData).getRows(index).get());	//10分間隔データの1時間ごとへの間引き

        //TimeSeries data = new TimeSeries(resultData).thin(6, TimeSeries.THIN_SIMPLE);
        String header = "time, outdoortemp, outdoorhumi, settemp, groundtemp, groundhumi, middletem, middlehumi, toptemp, tophumi, groundpmv, middlepmv, toppmv, electricenergy, coolingenergy";
        data.write("out.csv", header);
    }
}
