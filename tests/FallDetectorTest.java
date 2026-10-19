import br.com.socorro.FallDetector;
import br.com.socorro.SafetyTimer;
import br.com.socorro.WeatherRisk;

public final class FallDetectorTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static boolean g(FallDetector d,double g,long at,boolean gyro){return d.acceleration(0,0,(float)(g*9.80665),at,gyro);}
    private static boolean rest(FallDetector d,long at,boolean gyro){g(d,1,at,gyro);return g(d,1,at+1001,gyro);}
    public static void main(String[] args){
        FallDetector d=new FallDetector();
        check(!g(d,1,100,true),"Repouso não gera queda");
        g(d,4,2000,true);check(!rest(d,2200,true),"Sem rotação, impacto não gera queda");
        d.rotation(3,0,0,5000);check(!g(d,4,5100,true),"Impacto aguarda confirmação por repouso");
        check(rest(d,5300,true),"Impacto, rotação e repouso geram queda");
        check(d.peakG()>3.9,"Preserva intensidade do impacto");
        d.rotation(3,0,0,7000);g(d,5,7100,true);check(!rest(d,7300,true),"Intervalo evita duplicidade");
        d=new FallDetector();d.rotation(3,0,0,100);g(d,5,5000,true);check(!rest(d,5200,true),"Rotação antiga é ignorada");
        d=new FallDetector();d.context(true,false,0);d.rotation(3,0,0,1000);g(d,3.5,1100,true);check(!rest(d,1300,true),"Aparelho exposto exige evidência maior");
        d=new FallDetector();d.context(true,true,0);d.rotation(3,0,0,1000);g(d,3.5,1100,true);check(rest(d,1300,true),"Proximidade refina contexto");
        d=new FallDetector();d.context(true,false,0);g(d,0.1,900,true);d.rotation(3,0,0,1000);g(d,3.5,1100,true);check(rest(d,1300,true),"Queda livre apoia impacto com aparelho exposto");
        d=new FallDetector();d.context(true,false,1.1);d.rotation(3,0,0,1000);g(d,3.5,1100,true);check(rest(d,1300,true),"Barômetro auxilia contexto");
        d=new FallDetector();g(d,3.1,1000,false);check(!rest(d,1200,false),"Sem giroscópio, limite de impacto é maior");g(d,4,4000,false);check(rest(d,4200,false),"Fallback sem giroscópio confirma repouso");
        d=new FallDetector();d.rotation(3,0,0,1000);g(d,4,1100,true);check(!rest(d,7000,true),"Candidato expira após cinco segundos");
        d=new FallDetector();d.rotation(3,0,0,1000);g(d,4,1100,true);g(d,1,1200,true);g(d,2,1700,true);check(!g(d,1,2200,true),"Movimento reinicia repouso");check(!g(d,1,3000,true),"Repouso incompleto não confirma");check(g(d,1,3300,true),"Repouso contínuo confirma");
        check(!d.acceleration(Float.NaN,0,1,40000,true),"Leitura inválida é ignorada");
        SafetyTimer timer=new SafetyTimer();check(timer.begin(SafetyTimer.SOS,1000),"Inicia SOS");check(timer.seconds(1000)==30,"Contagem começa em 30");check(timer.seconds(1001)==30,"Arredonda segundos para cima");check(!timer.expired(30999),"Não expira antes do prazo");check(timer.expired(31000),"Expira no prazo exato");check(!timer.begin(SafetyTimer.FALL,32000),"Nova origem não substitui contagem ativa");timer.cancel();check(!timer.active()&&timer.deadline()==0,"Cancelar limpa estado");check(timer.begin(SafetyTimer.CHECK_IN,40000),"Check-in usa contagem comum");check(timer.reason()==SafetyTimer.CHECK_IN&&timer.expired(70000),"Check-in sem resposta expira");timer.cancel();check(timer.begin(SafetyTimer.FALL,80000),"Detector inicia nova contagem");
        check(WeatherRisk.warning(0,12,20,10,0).startsWith("Sem indicação"),"Tempo sem riscos configurados");check(WeatherRisk.warning(1,5,10,20,61).startsWith("Chuva"),"Chuva presente");check(WeatherRisk.warning(0,5,10,80,3).startsWith("Chuva"),"Probabilidade futura de chuva");check(WeatherRisk.warning(0,45,45,0,0).startsWith("Vento"),"Vento forte");check(WeatherRisk.warning(0,10,55,0,0).startsWith("Vento"),"Rajadas fortes");check(WeatherRisk.warning(0,5,5,0,95).startsWith("Trovoadas"),"Trovoadas");check(WeatherRisk.warning(0,5,5,0,45).startsWith("Neblina"),"Neblina");
        System.out.println(checks+" verificações passaram (detector, contagem e clima).");
    }
}
