package cat.iesabadell.videojocs.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/*
 * DTO de RESPOSTA per a una pàgina de resultats (sessió 4).
 * Retornar el Page de Spring directament genera un JSON amb molts camps interns i Spring avisa
 * que la seva estructura no és estable. Aquest és el JSON que decidim nosaltres:
 *   {"contingut": [...], "pagina": 0, "mida": 5, "total": 12, "totalPagines": 3}
 * <T>: serveix per a pàgines de qualsevol DTO.
 */
public record PaginaResposta<T>(List<T> contingut, int pagina, int mida, long total, int totalPagines) {

	public static <T> PaginaResposta<T> de(Page<T> p) {
		return new PaginaResposta<>(p.getContent(), p.getNumber(), p.getSize(),
				p.getTotalElements(), p.getTotalPages());
	}

}
