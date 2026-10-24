package cat.iesabadell.videojocs.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/*
 * DTO de PETICIÓ per publicar un videojoc complet. La forma és la que li va bé al client,
 * no la de les taules: l'estudi, les categories i les plataformes van pel NOM.
 *
 * {
 *   "nom": "Hades II",
 *   "estudi": "Supergiant Games",
 *   "motor": "Motor propi",
 *   "categories": ["Roguelike"],
 *   "preus": { "PC": 29.99, "Switch": 29.99 }
 * }
 */
public record VideojocPeticio(
		@NotBlank(message = "El nom és obligatori")
		String nom,

		@NotBlank(message = "L'estudi és obligatori")
		String estudi,

		// Opcional: si no hi és, el videojoc no té fitxa tècnica
		String motor,

		@NotNull(message = "Cal la llista de categories (pot ser buida)")
		List<String> categories,

		// Clau: nom de la plataforma. Valor: preu (ha de ser positiu)
		@NotNull(message = "Cal el mapa de preus (pot ser buit)")
		Map<String, @Positive(message = "Els preus han de ser positius") BigDecimal> preus) {
}
