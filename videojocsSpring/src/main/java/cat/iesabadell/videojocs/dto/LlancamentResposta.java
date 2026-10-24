package cat.iesabadell.videojocs.dto;

import java.math.BigDecimal;

import cat.iesabadell.videojocs.model.Llancament;

/*
 * DTO de RESPOSTA d'un llançament vist "des de fora" del videojoc:
 *   {"videojoc": "Hades", "plataforma": "Switch", "preu": 24.99}
 * Si retornéssim l'entitat Llancament, el videojoc no sortiria (porta @JsonIgnore) i no sabríem de quin joc és.
 * Llegeix llancament.videojoc, que és LAZY: s'ha de construir dins d'un mètode @Transactional.
 */
public record LlancamentResposta(String videojoc, String plataforma, BigDecimal preu) {

	public static LlancamentResposta de(Llancament l) {
		return new LlancamentResposta(l.getVideojoc().getNom(), l.getPlataforma().getNom(), l.getPreu());
	}

}
