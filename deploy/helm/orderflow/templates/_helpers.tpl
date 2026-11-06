{{- define "orderflow.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "orderflow.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "orderflow.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "orderflow.suffixedName" -}}
{{- $root := index . 0 -}}
{{- $suffix := index . 1 -}}
{{- $base := include "orderflow.fullname" $root -}}
{{- $maxBaseLength := sub 62 (len $suffix) -}}
{{- if lt $maxBaseLength 1 -}}
{{- fail (printf "resource name suffix %q is too long" $suffix) -}}
{{- end -}}
{{- printf "%s-%s" (trunc (int $maxBaseLength) $base | trimSuffix "-") $suffix | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "orderflow.serviceName" -}}
{{- $root := index . 0 -}}
{{- $service := index . 1 -}}
{{- include "orderflow.suffixedName" (list $root $service) -}}
{{- end -}}

{{- define "orderflow.managementServiceName" -}}
{{- $root := index . 0 -}}
{{- $service := index . 1 -}}
{{- include "orderflow.suffixedName" (list $root (printf "%s-management" $service)) -}}
{{- end -}}

{{- define "orderflow.commonConfigName" -}}
{{- include "orderflow.suffixedName" (list . "common") -}}
{{- end -}}

{{- define "orderflow.commonLabels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: orderflow
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "orderflow.selectorLabels" -}}
{{- $root := index . 0 -}}
{{- $service := index . 1 -}}
app.kubernetes.io/name: {{ $service }}
app.kubernetes.io/instance: {{ $root.Release.Name }}
{{- end -}}

{{- define "orderflow.image" -}}
{{- $root := index . 0 -}}
{{- $service := index . 1 -}}
{{- if $root.Values.image.registry }}{{ $root.Values.image.registry }}/{{ end }}{{ $service.image }}{{ if $service.digest }}@{{ $service.digest }}{{ else }}:{{ $root.Values.image.tag }}{{ end }}
{{- end -}}
