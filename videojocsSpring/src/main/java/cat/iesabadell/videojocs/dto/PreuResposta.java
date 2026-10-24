package cat.iesabadell.videojocs.dto;

import java.math.BigDecimal;

import cat.iesabadell.videojocs.model.Llancament;

/*
 * DTO de RESPOSTA d'un preu: {"plataforma": "PC", "preu": 29.99}
 * En lloc de l'objecte Llancament sencer (amb el seu id, el videojoc...), només el que interessa.
 */
public record PreuResposta(String plataforma, BigDecimal preu) {

	// Conversió entitat -> DTO en un sol lloc
	public static PreuResposta de(Llancament l) {
		return new PreuResposta(l.getPlataforma().getNom(), l.getPreu());
	}

}
