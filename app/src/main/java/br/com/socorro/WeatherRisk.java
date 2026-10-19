package br.com.socorro;

public final class WeatherRisk {
    private WeatherRisk() { }
    public static String warning(double rain, double wind, double gust, double probability, int code) {
        if (code >= 95) return "Trovoadas na região. Considere adiar a saída.";
        if (wind >= 40 || gust >= 50) return "Vento forte. Redobre o cuidado no trajeto.";
        if (rain > 0 || probability >= 70) return "Chuva no trajeto. Atenção à pista molhada.";
        if (code == 45 || code == 48) return "Neblina na região. Atenção à visibilidade.";
        return "Sem indicação de chuva ou vento forte nos dados consultados.";
    }
    public static String description(int code) {
        if (code == 0) return "Céu aberto";
        if (code <= 3) return "Entre nuvens";
        if (code == 45 || code == 48) return "Neblina";
        if (code >= 95) return "Trovoadas";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Neve";
        return "Chuva";
    }
}

