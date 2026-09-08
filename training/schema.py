from __future__ import annotations

from enum import Enum
from pydantic import BaseModel, Field


class MessageSource(str, Enum):
    STREAM_ASR = "STREAM_ASR"
    WHATSAPP_DIRECT = "WHATSAPP_DIRECT"
    WHATSAPP_GROUP = "WHATSAPP_GROUP"
    SMS = "SMS"
    EMAIL = "EMAIL"


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class ScamType(str, Enum):
    NONE = "None"
    FAKE_FAMILY_EXTORTION = "Falso Familiar / Extorsión Policial"
    BANK_IMPERSONATION = "Phishing / Suplantación Bancaria"
    GOVERNMENT_IMPERSONATION = "Suplantación Entidad Pública (Aduana / Impuestos)"
    TELECOM_FRAUD = "Fraude de Telecomunicaciones / Falso Soporte"


class FraudSynthesized(BaseModel):
    """Structured output from Gemini for fraud classification."""

    source_type: MessageSource = Field(
        description="Canal por donde ocurrió el fraude según el relato."
    )
    simulated_raw_text: str = Field(
        description=(
            "Reconstrucción exacta de lo que dijo o escribió el estafador. "
            "Usa modismos locales (ej. 'megas', 'tigo'). Si es llamada (ASR), "
            "escribe en minúsculas y sin puntuación. Omite nombres reales de las víctimas."
        )
    )
    rationale: str = Field(
        description="Análisis técnico de 1 a 2 oraciones sobre el modus operandi y la coerción utilizada."
    )
    is_scam: bool = Field(description="Verdadero si es un intento de fraude.")
    risk_level: RiskLevel = Field(description="Nivel de riesgo del intento de fraude.")
    scam_type: ScamType = Field(description="Categoría en la que encaja el fraude.")