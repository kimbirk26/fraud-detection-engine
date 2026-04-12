{{- define "fraud-detection-engine.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "fraud-detection-engine.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := include "fraud-detection-engine.name" . -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "fraud-detection-engine.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "fraud-detection-engine.labels" -}}
helm.sh/chart: {{ include "fraud-detection-engine.chart" . }}
app.kubernetes.io/name: {{ include "fraud-detection-engine.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "fraud-detection-engine.selectorLabels" -}}
app.kubernetes.io/name: {{ include "fraud-detection-engine.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "fraud-detection-engine.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "fraud-detection-engine.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "fraud-detection-engine.configMapName" -}}
{{- printf "%s-config" (include "fraud-detection-engine.fullname" .) -}}
{{- end -}}

{{- define "fraud-detection-engine.secretName" -}}
{{- if .Values.externalSecret.enabled -}}
{{- default (include "fraud-detection-engine.fullname" .) .Values.externalSecret.targetSecretName -}}
{{- else if .Values.secrets.existingSecretName -}}
{{- .Values.secrets.existingSecretName -}}
{{- else -}}
{{- include "fraud-detection-engine.fullname" . -}}
{{- end -}}
{{- end -}}
