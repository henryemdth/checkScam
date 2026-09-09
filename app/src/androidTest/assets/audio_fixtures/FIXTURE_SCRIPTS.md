# Audio Fixture Scripts

Manual recordings of realistic Bolivian vishing calls, consumed by
`EndToEndPipelineTest`. Record **as two speakers** (A = victim / local/loud,
B = scammer / remote/quiet) so the energy-based diarization can separate turns.

## Recording requirements

- **Format:** WAV, raw PCM, **16 000 Hz**, **mono**, **16-bit** (little-endian).
- **Duration:** ~25–40 s per take, enough that the scammer's turns each exceed
  ~60 characters of speech (the analyzer only fires once 60+ new chars arrive).
- **Placement:** record on a phone with **speakerphone ON**, caller on the line
  (or simulate the caller) so the victim (A) is loud and the scammer (B) is
  quieter — this replicates the mic+speakerphone capture strategy.
- **Naming:** exactly `telecom_scam.wav`, `bank_scam.wav`, `government_scam.wav`
  inside `app/src/androidTest/assets/audio_fixtures/`.
- **Recommended:** for each take, feed the whole two-sided conversation with
  short pauses at turn boundaries. Do NOT silence-strip — pauses let the
  diarizer attribute turns.
- Do not include real names or CI numbers of any actual person.

Suggested take style (read both roles yourself, adjusting energy for B):

---

## 1. Telecom Scam (`telecom_scam.wav`) — Tigo/Entel impersonation

> Context: scammer impersonates a telecom support agent calling to "fix" the
> line, pressures the victim into handing over an SMS verification code.

```
B:  hola muy buenos dias señora le llamamos de la central de tigo estamos
    realizando una actualizacion de seguridad en su linea y lamentablemente hemos
    detectado una irregularidad por lo que su numero sera bloqueado en los proximos
    minutos si no confirma sus datos ahora mismo
A:  pero de que numero me llama quien es usted
B:  soy del area de soporte tecnico de tigo su numero esta registrado en nuestro
    sistema le voy a mandar un codigo por mensaje de texto usted me lo dicta y
    quedan asignados sus megas con el plan de renovacion
A:  yo no pedi ninguna renovacion de megas no quiero que me bloqueen la linea
B:  si no me da el codigo no podemos reactivar su linea y pierde todos sus megas
    del saldo hoy mismo confirme ya por favor
```

---

## 2. Bank Spoofing (`bank_scam.wav`) — Banco Unión/BNB impersonation

> Context: scammer impersonates a bank fraud department claiming a suspicious
> withdrawal and walks the victim through "verification" to drain the account.

```
B:  buenas tardes habla del departamento de prevencion de fraude del banco union
    hemos detectado un intento de acceso no autorizado en su cuenta con un retiro
    de dos mil bolivianos de un cajero en santa cruz usted hizo esa operacion
A:  no yo no he movido mi cuenta no tengo tarjeta conmigo ahora
B:  por eso le llamamos para bloquear de inmediato necesito que me confirme el
    numero de su tarjeta y el codigo que le enviamos por mensaje de texto para
    revertir la operacion antes de que sea tarde
A:  no voy a dar mi tarjeta por telefono mejor voy a la agencia en persona
B:  si no confirma ahora mismo el cargo se ejecuta y no podremos recuperar sus
    fondos usted es responsable de la perdida
```

---

## 3. Government Extortion (`government_scam.wav`) — Aduana/Policía impersonation

> Context: scammer impersonates Aduana or the Policía claiming a confiscated
> parcel and demanding a payment/fine to avoid prosecution or seizure.

```
B:  le hablamos de la policia nacional tenemos registrado un paquete a su nombre
    retenido en la aduana de la paz con mercaderia sin declarar si no paga la
    multa de tramite ahora mismo se procede a confiscar y se abre un proceso penal
    en su contra
A:  yo no he recibido ningun paquete eso debe ser un error
B:  el paquete figura a su nombre y carnet el fiscal de aduana exige el deposito
    de mil quinientos bolivianos para liberarlo y cerrar el caso si no quiere ir
    detenido tiene que pagar hoy
A:  voy a hacer la denuncia directamente en la oficina de la aduana esto no
    puede ser cierto
```

---

## Verification (before running the test)

Quick sanity check of any take:

```bash
ffprobe -show_entries stream=sample_rate,channels,codec_name -of compact \
  app/src/androidTest/assets/audio_fixtures/telecom_scam.wav
```

Expect: `codec_name=pcm_s16le`, `sample_rate=16000`, `channels=1`. Re-encode if
your recorder produced stereo or a different rate (e.g.
`ffmpeg -i in.m4a -ar 16000 -ac 1 -c:a pcm_s16le telecom_scam.wav`).