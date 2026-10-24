package cat.iesabadell.videojocs.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cat.iesabadell.videojocs.model.Categoria;
import cat.iesabadell.videojocs.model.Llancament;
import cat.iesabadell.videojocs.model.Videojoc;

/*
 * DTO de RESPOSTA amb el DETALL d'un videojoc.
 * {
 *   "id": 1,
 *   "nom": "The Witcher 3",
 *   "estudi": "CD Projekt Red",
 *   "motor": "REDengine 3",
 *   "categories": ["RPG"],
 *   "preus": [ {"plataforma": "PC", "preu": 29.99}, ... ]
 * }
 * Resol els tres problemes de retornar l'entitat:
 *   - no hi ha relacions bidireccionals -> no hi ha bucle infinit
 *   - es construeix DINS del servei     -> els llançaments (LAZY) es poden llegir
 *   - només hi ha String i números      -> cap proxy d'Hibernate arriba a Jackson
 */
public record VideojocResposta(Integer id, String nom, String estudi, String motor,
		List<String> categories, List<PreuResposta> preus) {

	// COMPTE: s'ha de cridar DINS d'un mètode @Transactional (llegeix videojoc.llancaments, que és LAZY)
	public static VideojocResposta de(Videojoc v) {
		String motor = null;
		if (v.getFitxa() != null) {
			motor = v.getFitxa().getMotor();
		}

		// Categories amb la seva ruta a l'arbre: "Acció > Roguelike"
		ArrayList<String> categories = new ArrayList<>();
		for (Categoria c : v.getCategories()) {
			categories.add(ruta(c));
		}
		Collections.sort(categories);

		ArrayList<PreuResposta> preus = new ArrayList<>();
		for (Llancament l : v.getLlancaments()) {
			preus.add(PreuResposta.de(l));
		}

		return new VideojocResposta(v.getId(), v.getNom(), v.getEstudi().getNom(), motor, categories, preus);
	}

	// Puja per l'arbre fins a l'arrel (pare és EAGER i la cadena és curta)
	private static String ruta(Categoria c) {
		if (c.getPare() == null) {
			return c.getNom();
		}
		return ruta(c.getPare()) + " > " + c.getNom();
	}

}
