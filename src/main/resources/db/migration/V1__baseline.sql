-- V1__baseline.sql (T-044)
--
-- Snapshot схемы после T-076 (auto_advance_rounds) + T-001 (index.sql: pg_trgm,
-- idx_session_name_trgm, ix_npc_profile_user_id) — сгенерировано pg_dump из БД,
-- поднятой @SpringBootTest с ddl-auto=update, sanitize'нутый (SET/pg_catalog/
-- \\restrict убраны). Дальше — только incremental V2__, V3__...




--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;




--
-- Name: decision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.decision (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    decision boolean NOT NULL,
    offer_id uuid NOT NULL,
    responder_id uuid NOT NULL,
    round_id uuid NOT NULL,
    session_id uuid NOT NULL
);


--
-- Name: npc_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.npc_profile (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    params_json jsonb NOT NULL,
    seed bigint,
    strategy character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT npc_profile_strategy_check CHECK (((strategy)::text = ANY ((ARRAY['FAIR'::character varying, 'SELFISH'::character varying, 'RANDOM'::character varying, 'VENGEFUL'::character varying, 'ADAPTIVE'::character varying])::text[])))
);


--
-- Name: offer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    offer_value integer NOT NULL,
    proposer_id uuid NOT NULL,
    responder_id uuid,
    round_id uuid NOT NULL,
    session_id uuid NOT NULL
);


--
-- Name: round; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.round (
    id uuid NOT NULL,
    round_number integer NOT NULL,
    round_phase character varying(255) NOT NULL,
    session_id uuid NOT NULL,
    CONSTRAINT round_round_phase_check CHECK (((round_phase)::text = ANY ((ARRAY['CREATED'::character varying, 'WAIT_OFFERS'::character varying, 'ALL_OFFERS_RECEIVED'::character varying, 'OFFERS_SENT'::character varying, 'ALL_DECISIONS_RECEIVED'::character varying, 'FINISHED'::character varying, 'ABORTED'::character varying])::text[])))
);


--
-- Name: session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session (
    id uuid NOT NULL,
    num_players integer NOT NULL,
    num_rounds integer NOT NULL,
    num_teams integer NOT NULL,
    round_sum integer NOT NULL,
    session_type character varying(255) NOT NULL,
    timeout_move_sec integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    display_name character varying(255) NOT NULL,
    open_to_connect boolean NOT NULL,
    state character varying(255) NOT NULL,
    admin_id uuid NOT NULL,
    current_round_id uuid,
    auto_advance_rounds boolean DEFAULT false NOT NULL,
    CONSTRAINT session_session_type_check CHECK (((session_type)::text = ANY ((ARRAY['FREE_FOR_ALL'::character varying, 'TEAM_BATTLE'::character varying])::text[]))),
    CONSTRAINT session_state_check CHECK (((state)::text = ANY ((ARRAY['CREATED'::character varying, 'RUNNING'::character varying, 'FINISHED'::character varying, 'ABORTED'::character varying])::text[])))
);


--
-- Name: session_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_members (
    session_id uuid NOT NULL,
    members_id uuid NOT NULL
);


--
-- Name: session_observers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_observers (
    session_id uuid NOT NULL,
    observers_id uuid NOT NULL
);


--
-- Name: team; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    session_id uuid NOT NULL
);


--
-- Name: team_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_members (
    team_id uuid NOT NULL,
    members_id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    nickname character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'PLAYER'::character varying, 'OBSERVER'::character varying, 'NPC'::character varying])::text[])))
);


--
-- Name: decision decision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision
    ADD CONSTRAINT decision_pkey PRIMARY KEY (id);


--
-- Name: npc_profile npc_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.npc_profile
    ADD CONSTRAINT npc_profile_pkey PRIMARY KEY (id);


--
-- Name: offer offer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer
    ADD CONSTRAINT offer_pkey PRIMARY KEY (id);


--
-- Name: round round_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.round
    ADD CONSTRAINT round_pkey PRIMARY KEY (id);


--
-- Name: session_members session_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_members
    ADD CONSTRAINT session_members_pkey PRIMARY KEY (session_id, members_id);


--
-- Name: session_observers session_observers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_observers
    ADD CONSTRAINT session_observers_pkey PRIMARY KEY (session_id, observers_id);


--
-- Name: session session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);


--
-- Name: team_members team_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT team_members_pkey PRIMARY KEY (team_id, members_id);


--
-- Name: team team_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team
    ADD CONSTRAINT team_pkey PRIMARY KEY (id);


--
-- Name: npc_profile uk4ubr8t5m5shb5gqjkqhlh8qgr; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.npc_profile
    ADD CONSTRAINT uk4ubr8t5m5shb5gqjkqhlh8qgr UNIQUE (user_id);


--
-- Name: session uk71yqql825gxpqg3o79m7ghhcj; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT uk71yqql825gxpqg3o79m7ghhcj UNIQUE (current_round_id);


--
-- Name: decision uk9kv4h5rt50swgyof59om0mkmg; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision
    ADD CONSTRAINT uk9kv4h5rt50swgyof59om0mkmg UNIQUE (offer_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_session_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_name_trgm ON public.session USING gin (display_name public.gin_trgm_ops);


--
-- Name: ix_npc_profile_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ix_npc_profile_user_id ON public.npc_profile USING btree (user_id);


--
-- Name: session_members fk150u5b26pn1j619v4m9jtqvby; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_members
    ADD CONSTRAINT fk150u5b26pn1j619v4m9jtqvby FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: npc_profile fk367pplhw46ewqqlxtneggtwft; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.npc_profile
    ADD CONSTRAINT fk367pplhw46ewqqlxtneggtwft FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: session_observers fk3yyxppf833uj0voodyplp10wv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_observers
    ADD CONSTRAINT fk3yyxppf833uj0voodyplp10wv FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: team_members fk4rl09ugxbntgyfhogal3s67ji; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT fk4rl09ugxbntgyfhogal3s67ji FOREIGN KEY (members_id) REFERENCES public.users(id);


--
-- Name: session fk79bmkpgtrxbirw5ddd775t9bs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT fk79bmkpgtrxbirw5ddd775t9bs FOREIGN KEY (current_round_id) REFERENCES public.round(id);


--
-- Name: offer fk7ornumlm7bduxfbnlnnix7ck7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer
    ADD CONSTRAINT fk7ornumlm7bduxfbnlnnix7ck7 FOREIGN KEY (proposer_id) REFERENCES public.users(id);


--
-- Name: offer fkadawcre60yra46igg9nr5ghp6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer
    ADD CONSTRAINT fkadawcre60yra46igg9nr5ghp6 FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: decision fkawgni7925w6neu3vv3pgf4c56; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision
    ADD CONSTRAINT fkawgni7925w6neu3vv3pgf4c56 FOREIGN KEY (offer_id) REFERENCES public.offer(id);


--
-- Name: team_members fkb3toat7ors5scfmd3n69dhmr1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT fkb3toat7ors5scfmd3n69dhmr1 FOREIGN KEY (team_id) REFERENCES public.team(id);


--
-- Name: decision fkbc03pr3o5a4cxawrtpqr9dm4f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision
    ADD CONSTRAINT fkbc03pr3o5a4cxawrtpqr9dm4f FOREIGN KEY (responder_id) REFERENCES public.users(id);


--
-- Name: session_observers fkbe5e0yfsb2d9m5b0gasjp82j3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_observers
    ADD CONSTRAINT fkbe5e0yfsb2d9m5b0gasjp82j3 FOREIGN KEY (observers_id) REFERENCES public.users(id);


--
-- Name: session_members fkdckobko7hpkjkxoj745a1x5ci; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_members
    ADD CONSTRAINT fkdckobko7hpkjkxoj745a1x5ci FOREIGN KEY (members_id) REFERENCES public.users(id);


--
-- Name: offer fkeinhqflvhghv345on6ket2hbl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer
    ADD CONSTRAINT fkeinhqflvhghv345on6ket2hbl FOREIGN KEY (round_id) REFERENCES public.round(id);


--
-- Name: session fkfub3b7mbo8gxf8uw4hi780i1s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT fkfub3b7mbo8gxf8uw4hi780i1s FOREIGN KEY (admin_id) REFERENCES public.users(id);


--
-- Name: round fkg7g56neucugaofstva4i831kb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.round
    ADD CONSTRAINT fkg7g56neucugaofstva4i831kb FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: team fkokjexettbnb5bmo0wtkih5sok; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team
    ADD CONSTRAINT fkokjexettbnb5bmo0wtkih5sok FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: decision fkovgwttr4v0hb2jbudw4cpvafu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision
    ADD CONSTRAINT fkovgwttr4v0hb2jbudw4cpvafu FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: offer fkr219tsy7hpmt73vfq4iip13o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer
    ADD CONSTRAINT fkr219tsy7hpmt73vfq4iip13o FOREIGN KEY (responder_id) REFERENCES public.users(id);


--
-- Name: decision fkstambbuhh8ed5vbyo2gmxcsx6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.decision
    ADD CONSTRAINT fkstambbuhh8ed5vbyo2gmxcsx6 FOREIGN KEY (round_id) REFERENCES public.round(id);


--
--


