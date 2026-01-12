{{- define "availableXaasesList" -}}
  dbaas
  {{- if (eq (toString .Values.MAAS_ENABLED) "true") -}}
     {{- print ",maas" }}
  {{- end -}}
{{- end -}}


{{- define "to_millicores" -}}
{{- $v := printf "%v" . -}}
{{- if hasSuffix $v "m" -}}
  {{- trimSuffix "m" $v | int -}}
{{- else -}}
  {{- $f := toFloat64 $v -}}
  {{- int (mulf $f 1000) -}}
{{- end -}}
{{- end -}}