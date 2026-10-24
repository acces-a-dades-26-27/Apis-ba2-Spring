package cat.iesabadell.videojocs.dto;

import cat.iesabadell.videojocs.model.Videojoc;

/*
 * DTO de RESPOSTA per a LLISTATS: només el que cal per mostrar una llista.
 *   {"id": 1, "nom": "The Witcher 3", "estudi": "CD Projekt Red"}
 * El detall complet (fitxa, categories, preus) és a VideojocResposta.
 * Un mateix recurs pot tenir DTO diferents segons l'endpoint: llista curta, detall llarg.
 */
public record VideojocResum(Integer id, String nom, String estudi) {

	// Llegeix videojoc.estudi (EAGER): es pot cridar fora del servei, però el fem servir a dins igualment
	public static VideojocResum de(Videojoc v) {
		return new VideojocResum(v.getId(), v.getNom(), v.getEstudi().getNom());
	}

}
