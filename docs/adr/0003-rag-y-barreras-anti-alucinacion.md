# ADR 0003: RAG con embeddings locales y barreras anti-alucinación en capas

Estado: aceptado

## Contexto

El asistente debe responder solo con base en tres protocolos clínicos y decir que no existe protocolo cuando la pregunta no está cubierta (RN-05). En medicina, una respuesta inventada es peor que ninguna. El corpus es pequeño (3 protocolos, 7 secciones) y cambia poco.

## Decisión

**LangChain4j** como capa de RAG y cliente del LLM (API compatible con OpenAI). El corpus se carga de `corpus/protocolos.json` y se indexa un segmento por sección (título, sección y contenido, con metadata de protocolo y sección) en un `InMemoryEmbeddingStore`, al arrancar y antes de estar READY.

**Embeddings ONNX locales** (all-MiniLM-L6-v2 cuantizado, dentro del jar) y almacén en memoria. Con tan pocos segmentos no se justifica una base vectorial. Como la búsqueda no sale a ningún proveedor, es gratuita y no depende de la disponibilidad ni de la clave del LLM: por eso una pregunta sin protocolo se responde aunque el LLM esté caído. El corpus está versionado en el repositorio, así que reindexar es reiniciar. Los embeddings se calculan en un scheduler aparte (`boundedElastic`) porque son CPU.

**Compuerta de relevancia híbrida.** El modelo es sobre todo inglés y sus scores en español no separan bien las preguntas ajenas al corpus; por eso el umbral de similitud (`asistente.rag.score-minimo=0.65`) solo descarta lo claramente lejano. `CompuertaRelevancia` exige además una de dos cosas: que algún segmento supere un score alto (`score-alto=0.80`), o que la pregunta comparta alguna raíz con los términos discriminantes. Estos se derivan del propio corpus: raíces que aparecen en un único protocolo, así que no hay una lista a mano que mantener. Si nada pasa, se responde el aviso de "no existe protocolo" sin llamar al LLM.

**Barreras en capas**, cada una asume que la anterior pudo fallar:
1. Compuerta de relevancia, antes del LLM.
2. Prompt: responder solo con el contexto, copiar cifras tal cual y devolver JSON con `sinProtocolo`. La pregunta va delimitada y se trata como dato, no como instrucción.
3. Si el modelo marca `sinProtocolo=true`, se respeta.
4. `RespuestaValidator` invalida la respuesta si no cita fuentes, si alguna fuente no existe en el corpus ni entre los segmentos recuperados, si alguna cifra no aparece en las secciones citadas, o si la cobertura léxica respecto del contenido citado queda bajo 0.85 (`asistente.validacion.cobertura-minima`). Para esa cobertura no cuentan las palabras de la pregunta ni palabras meta como "según" o "figura"; con 0.80 una respuesta fiel con un fármaco añadido al final pasaba, y con 0.85 no. Además, toda mención `PROTO-n` del texto debe estar entre las fuentes citadas.
5. Una respuesta inválida se reemplaza por el aviso estándar con `fuentes: []` y se cuenta en `asistente.respuestas{tipo=invalida}`.

## Alternativas consideradas

- pgvector: agrega una base y migraciones para unos pocos vectores. Sería lo natural si el corpus creciera a cientos de documentos o se editara en caliente.
- Azure AI Search: administrado y con búsqueda híbrida, pero con costo, dependencia de la nube y menos reproducible en local.
- Modelo de embeddings multilingüe: mejoraría los scores en español, pero pesa mucho más en el jar y en memoria. Queda como mejora.
- Solo umbral de score: con consultas ajenas y parafraseadas no separaba bien; de ahí la compuerta.

## Consecuencias

- Las preguntas ajenas se rechazan sin costo de LLM. Una pregunta legítima con vocabulario que ningún término discriminante cubre y score bajo puede recibir el aviso de "sin protocolo" (falso negativo). Se prefiere ese error al contrario.
- La compuerta léxica depende del idioma del corpus; si se agregan protocolos en otro idioma hay que revisarla.
- El indexado al arranque suma segundos al inicio del servicio.
- Cambiar el corpus exige redeploy.
