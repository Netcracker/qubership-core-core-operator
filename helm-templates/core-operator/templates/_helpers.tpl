{{- define "availableXaasesList" -}}
  dbaas
  {{- if (eq (toString .Values.MAAS_ENABLED) "true") -}}
     {{- print ",maas" }}
  {{- end -}}
{{- end -}}