package cat.iesabadell.videojocs.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cat.iesabadell.videojocs.dto.PaginaResposta;
import cat.iesabadell.videojocs.dto.VideojocPeticio;
import cat.iesabadell.videojocs.dto.VideojocResposta;
import cat.iesabadell.videojocs.dto.VideojocResum;
import cat.iesabadell.videojocs.service.VideojocService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/*
 * Videojocs (sessions 3 i 4). Totes les respostes són DTO:
 *   llistats -> VideojocResum      detall -> VideojocResposta
 */
@RestController
@RequestMapping(path="/api/videojocs")
public class VideojocController {

	@Autowired
	private VideojocService service;

	/*
	 * GET localhost:8080/api/videojocs
	 * GET localhost:8080/api/videojocs?categoria=RPG
	 * required=false: si no hi és, arriba null i el servei els retorna tots
	 */
	@GetMapping
	public ResponseEntity<List<VideojocResum>> llistar(@RequestParam(required = false) String categoria) {
		return service.llistar(categoria);
	}

	// GET localhost:8080/api/videojocs/complet -> tots amb el detall (N+1 resolt amb @EntityGraph)
	@GetMapping(path="/complet")
	public ResponseEntity<List<VideojocResposta>> llistarComplet() {
		return service.llistarComplet();
	}

	// GET localhost:8080/api/videojocs/pagina?pagina=0&mida=2
	@GetMapping(path="/pagina")
	public ResponseEntity<PaginaResposta<VideojocResum>> pagina(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "La pàgina comença a 0") int pagina,
			@RequestParam(defaultValue = "5") @Min(value = 1, message = "La mida mínima és 1")
					@Max(value = 50, message = "La mida màxima és 50") int mida) {
		return service.pagina(pagina, mida);
	}

	// GET localhost:8080/api/videojocs/1
	@GetMapping(path="/{id}")
	public ResponseEntity<?> mostrar(@PathVariable int id) {
		return service.mostrar(id);
	}

	/*
	 * POST localhost:8080/api/videojocs
	 * {"nom": "Hades II", "estudi": "Supergiant Games", "motor": "Motor propi",
	 *  "categories": ["Roguelike"], "preus": {"PC": 29.99, "Switch": 29.99}}
	 */
	@PostMapping
	public ResponseEntity<?> publicar(@Valid @RequestBody VideojocPeticio peticio) {
		return service.publicar(peticio);
	}

	// POST localhost:8080/api/videojocs/3/categories/RPG
	@PostMapping(path="/{id}/categories/{categoria}")
	public ResponseEntity<?> afegirCategoria(@PathVariable int id, @PathVariable String categoria) {
		return service.afegirCategoria(id, categoria);
	}

	// DELETE localhost:8080/api/videojocs/3/categories/RPG
	@DeleteMapping(path="/{id}/categories/{categoria}")
	public ResponseEntity<?> treureCategoria(@PathVariable int id, @PathVariable String categoria) {
		return service.treureCategoria(id, categoria);
	}

	// POST localhost:8080/api/videojocs/3/llancaments?plataforma=PS5&preu=24.99
	@PostMapping(path="/{id}/llancaments")
	public ResponseEntity<?> afegirLlancament(
			@PathVariable int id,
			@RequestParam String plataforma,
			@RequestParam @Positive(message = "El preu ha de ser positiu") BigDecimal preu) {
		return service.afegirLlancament(id, plataforma, preu);
	}

	// DELETE localhost:8080/api/videojocs/3
	@DeleteMapping(path="/{id}")
	public ResponseEntity<String> esborrar(@PathVariable int id) {
		return service.esborrar(id);
	}

	/*
	 * POST localhost:8080/api/videojocs/descompte?categoria=Acció&import=5
	 * Abaixa el preu dels videojocs de la categoria i de totes les seves subcategories.
	 * "import" és una paraula reservada de Java: el paràmetre de l'URL es diu import i el de Java, descompte
	 */
	@PostMapping(path="/descompte")
	public ResponseEntity<String> descompte(
			@RequestParam String categoria,
			@RequestParam("import") @Positive(message = "El descompte ha de ser positiu") BigDecimal descompte) {
		return service.abaixaPreuPerCategoria(categoria, descompte);
	}

}
