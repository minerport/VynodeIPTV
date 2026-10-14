package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

type Channel struct {
	ID           string `json:"id"`
	ProviderID   string `json:"providerId"`
	StreamID     string `json:"streamId"`
	Name         string `json:"name"`
	Category     string `json:"category"`
	EPGID        string `json:"epgId"`
	CategoryName string `json:"categoryName"`
}
type Programme struct {
	Description string    `json:"description,omitempty"`
	Year        string    `json:"year,omitempty"`
	ChannelID   string    `json:"channelId"`
	Title       string    `json:"title"`
	Start       time.Time `json:"start"`
	End         time.Time `json:"end"`
}

func validateProvider(p Provider) error {
	u, e := url.Parse(p.URL)
	if e != nil || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Scheme != "http" && u.Scheme != "https") {
		return errors.New("Use an HTTP(S) server URL without credentials, query or fragment")
	}
	if u.Scheme == "http" && !p.AllowHTTP {
		return errors.New("HTTP requires explicit Allow HTTP selection")
	}
	if p.HTTPFallback && !p.AllowHTTP {
		return errors.New("Automatic HTTP fallback requires Allow HTTP permission")
	}
	if p.Username == "" || p.Password == "" || p.Name == "" {
		return errors.New("Name, username and password are required")
	}
	if p.Format != "ts" && p.Format != "m3u8" {
		return errors.New("Choose ts or m3u8")
	}
	if p.MaxConnections < 1 || p.MaxConnections > 32 {
		return errors.New("Connection limit must be 1–32")
	}
	return nil
}
func providerURL(p Provider, endpoint string, action string) string {
	u, _ := url.Parse(strings.TrimRight(p.URL, "/") + "/" + endpoint)
	q := u.Query()
	q.Set("username", p.Username)
	q.Set("password", p.Password)
	if action != "" {
		q.Set("action", action)
	}
	u.RawQuery = q.Encode()
	return u.String()
}
func streamURL(p Provider, c Channel) string {
	return strings.TrimRight(p.URL, "/") + "/live/" + url.PathEscape(p.Username) + "/" + url.PathEscape(p.Password) + "/" + url.PathEscape(c.StreamID) + "." + p.Format
}
func providerGet(ctx context.Context, p Provider, endpoint, action string) (io.ReadCloser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, providerURL(p, endpoint, action), nil)
	if err != nil {
		return nil, errors.New("Invalid provider URL")
	}
	client := &http.Client{Timeout: 60 * time.Second, CheckRedirect: func(r *http.Request, via []*http.Request) error {
		if len(via) > 5 {
			return errors.New("Too many redirects")
		}
		if r.URL.Scheme != "https" && !(p.AllowHTTP && r.URL.Scheme == "http") {
			return errors.New("Redirect transport not allowed")
		}
		if r.URL.Host != via[0].URL.Host {
			return errors.New("Cross-host API redirect blocked; use the canonical provider URL")
		}
		return nil
	}}
	resp, err := doWithHTTPFallback(client, req, p.HTTPFallback && p.AllowHTTP)
	if err != nil {
		return nil, providerConnectionError(err)
	}
	if resp.StatusCode != 200 {
		resp.Body.Close()
		return nil, fmt.Errorf("Provider returned HTTP %d", resp.StatusCode)
	}
	return resp.Body, nil
}
func loadChannels(ctx context.Context, p Provider) ([]Channel, error) {
	body, err := providerGet(ctx, p, "player_api.php", "")
	if err != nil {
		return nil, err
	}
	var account struct {
		UserInfo struct {
			Auth   json.RawMessage `json:"auth"`
			Status string          `json:"status"`
		} `json:"user_info"`
	}
	err = json.NewDecoder(io.LimitReader(body, 1<<20)).Decode(&account)
	body.Close()
	if err != nil {
		return nil, errors.New("Invalid account response")
	}
	if string(account.UserInfo.Auth) != "1" && string(account.UserInfo.Auth) != "\"1\"" {
		return nil, errors.New("Provider rejected credentials")
	}
	if account.UserInfo.Status != "" && !strings.EqualFold(account.UserInfo.Status, "Active") {
		return nil, errors.New("Provider account is not active")
	}
	categories := map[string]string{}
	if categoryBody, categoryErr := providerGet(ctx, p, "player_api.php", "get_live_categories"); categoryErr == nil {
		var rows []struct {
			ID   json.RawMessage `json:"category_id"`
			Name string          `json:"category_name"`
		}
		if json.NewDecoder(io.LimitReader(categoryBody, 4<<20)).Decode(&rows) == nil {
			for _, row := range rows {
				categories[strings.Trim(string(row.ID), "\"")] = row.Name
			}
		}
		categoryBody.Close()
	}
	body, err = providerGet(ctx, p, "player_api.php", "get_live_streams")
	if err != nil {
		return nil, err
	}
	defer body.Close()
	var rows []struct {
		ID       json.RawMessage `json:"stream_id"`
		Name     string          `json:"name"`
		Category json.RawMessage `json:"category_id"`
		EPG      string          `json:"epg_channel_id"`
	}
	if err = json.NewDecoder(io.LimitReader(body, 32<<20)).Decode(&rows); err != nil {
		return nil, errors.New("Invalid channel response or catalog exceeds 32 MB")
	}
	out := make([]Channel, 0, len(rows))
	seen := map[string]bool{}
	for _, r := range rows {
		id := strings.Trim(string(r.ID), "\"")
		if _, err := strconv.ParseUint(id, 10, 64); err != nil {
			continue
		}
		if seen[id] {
			continue
		}
		seen[id] = true
		categoryID := strings.Trim(string(r.Category), "\"")
		categoryName := categories[categoryID]
		if categoryName == "" {
			categoryName = "Other channels"
		}
		out = append(out, Channel{ID: p.ID + ":" + id, ProviderID: p.ID, StreamID: id, Name: r.Name, Category: categoryID, EPGID: r.EPG, CategoryName: categoryName})
	}
	if len(out) == 0 {
		return nil, errors.New("Provider returned no usable live channels")
	}
	return out, nil
}
func xmlTime(s string) (time.Time, error) {
	for _, layout := range []string{"20060102150405 -0700", "200601021504 -0700", "20060102150405", "200601021504"} {
		if t, e := time.Parse(layout, strings.TrimSpace(s)); e == nil {
			return t.UTC(), nil
		}
	}
	return time.Time{}, errors.New("Invalid XMLTV time")
}
func parseEPG(r io.Reader, channels []Channel, now time.Time) ([]Programme, error) {
	mapping := map[string][]string{}
	for _, c := range channels {
		if c.EPGID != "" {
			mapping[c.EPGID] = append(mapping[c.EPGID], c.ID)
		}
	}
	d := xml.NewDecoder(io.LimitReader(r, 128<<20))
	out := []Programme{}
	count := 0
	for {
		t, err := d.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, errors.New("Invalid or oversized XMLTV document")
		}
		start, ok := t.(xml.StartElement)
		if !ok || start.Name.Local != "programme" {
			continue
		}
		count++
		if count > 500000 {
			return nil, errors.New("XMLTV exceeds 500000 programme limit")
		}
		var p struct {
			Channel     string `xml:"channel,attr"`
			Start       string `xml:"start,attr"`
			Stop        string `xml:"stop,attr"`
			Title       string `xml:"title"`
			Description string `xml:"desc"`
			Date        string `xml:"date"`
		}
		if err = d.DecodeElement(&p, &start); err != nil {
			return nil, errors.New("Invalid XMLTV programme")
		}
		a, e := xmlTime(p.Start)
		b, e2 := xmlTime(p.Stop)
		if e != nil || e2 != nil || !b.After(a) || b.Before(now.Add(-7*24*time.Hour)) || a.After(now.Add(7*24*time.Hour)) {
			continue
		}
		if strings.TrimSpace(p.Title) == "" {
			p.Title = "Untitled programme"
		}
		if len(p.Title) > 4096 {
			p.Title = p.Title[:4096]
		}
		for _, id := range mapping[p.Channel] {
			year := ""
			if len(p.Date) >= 4 {
				year = p.Date[:4]
			}
			if len(p.Description) > 8000 {
				p.Description = p.Description[:8000]
			}
			out = append(out, Programme{ChannelID: id, Title: p.Title, Start: a, End: b, Description: p.Description, Year: year})
		}
	}
	if len(out) == 0 {
		return nil, errors.New("No matching programmes; previous guide retained")
	}
	return out, nil
}

// Never expose net/url errors: their URL contains the provider password.
func providerConnectionError(err error) error {
	var invalid x509.CertificateInvalidError
	var authority x509.UnknownAuthorityError
	var hostname x509.HostnameError
	var dns *net.DNSError
	var network net.Error
	switch {
	case errors.As(err, &invalid):
		return errors.New("Provider HTTPS certificate is expired or invalid")
	case errors.As(err, &authority):
		return errors.New("Provider HTTPS certificate is not trusted")
	case errors.As(err, &hostname):
		return errors.New("Provider HTTPS certificate does not match its address")
	case errors.As(err, &dns):
		return errors.New("Provider address could not be resolved")
	case errors.As(err, &network) && network.Timeout():
		return errors.New("Provider connection timed out")
	default:
		return errors.New("Provider connection or redirect failed")
	}
}

func doWithHTTPFallback(client *http.Client, req *http.Request, enabled bool) (*http.Response, error) {
	resp, err := client.Do(req)
	if err == nil || !enabled || req.URL.Scheme != "https" || req.Context().Err() != nil {
		return resp, err
	}
	var wrapped *url.Error
	if errors.As(err, &wrapped) {
		failed, e := url.Parse(wrapped.URL)
		if e != nil || failed.Hostname() != req.URL.Hostname() {
			return resp, err
		}
	}
	var certificate *tls.CertificateVerificationError
	var network *net.OpError
	if !errors.As(err, &certificate) && !errors.As(err, &network) {
		return resp, err
	}
	next := req.Clone(req.Context())
	u := *req.URL
	u.Scheme = "http"
	if u.Port() == "443" {
		u.Host = u.Hostname()
		if strings.Contains(u.Host, ":") {
			u.Host = "[" + u.Host + "]"
		}
	}
	next.URL = &u
	next.Host = u.Host
	return client.Do(next)
}
