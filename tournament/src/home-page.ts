export const HOME_PAGE = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#f4f0e8">
  <title>YIMO Graphwar</title>
  <style>
    :root{color-scheme:light;--ink:#101916;--muted:#58635d;--paper:#f4f0e8;--line:#d4cbbc;--orange:#ee8c39;--green:#183a32}
    *{box-sizing:border-box}
    body{margin:0;min-height:100vh;background:var(--paper);color:var(--ink);font:16px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}
    main{width:min(1120px,100%);min-height:100vh;margin:auto;padding:28px 28px 36px;display:flex;flex-direction:column}
    nav{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line);padding-bottom:18px}
    .brand{color:var(--green);font:700 21px Georgia,serif;letter-spacing:.08em}
    nav a,.source{color:var(--ink);font-weight:650;text-decoration:none}
    .hero{display:grid;grid-template-columns:minmax(0,1fr) minmax(260px,.82fr);align-items:center;gap:clamp(28px,6vw,76px);flex:1;padding:56px 0}
    .eyebrow{margin:0 0 14px;color:#a84f12;font-size:12px;font-weight:750;letter-spacing:.16em;text-transform:uppercase}
    h1{margin:0;max-width:660px;font-size:clamp(48px,8vw,92px);line-height:.92;letter-spacing:-.065em}
    h1 span{color:#c86e2c}
    .intro{max-width:460px;margin:24px 0 30px;color:var(--muted);font-size:18px}
    .links{display:flex;flex-wrap:wrap;align-items:center;gap:12px}
    .button{min-height:50px;padding:13px 23px;border:1px solid var(--ink);border-radius:999px;background:var(--ink);color:#fff;font-weight:700;text-decoration:none;transition:transform .16s ease,background .16s ease}
    .button:hover{transform:translateY(-2px);background:var(--green)}
    .button:focus-visible,.source:focus-visible,nav a:focus-visible{outline:3px solid var(--orange);outline-offset:4px}
    .button.secondary{background:transparent;color:var(--ink)}
    .button.secondary:hover{background:#e8e0d2}
    .graph{width:100%;max-height:390px;border:1px solid var(--line);border-radius:4px;background:#fffdf8}
    footer{display:flex;justify-content:space-between;gap:16px;border-top:1px solid var(--line);padding-top:16px;color:var(--muted);font-size:13px}
    @media(max-width:700px){main{padding:20px 18px}.hero{grid-template-columns:1fr;padding:48px 0 34px}.graph{max-height:270px}.intro{font-size:16px}footer{flex-direction:column}}
    @media(prefers-reduced-motion:reduce){*,*::before,*::after{scroll-behavior:auto!important;transition:none!important}}
  </style>
</head>
<body>
  <main>
    <nav aria-label="YIMO Graphwar">
      <div class="brand">YIMO / GRAPHWAR</div>
      <a href="https://github.com/goncalofrankefranco/yimo-graphwar" rel="noopener noreferrer" target="_blank">Source ↗</a>
    </nav>
    <section class="hero" aria-labelledby="title">
      <div>
        <p class="eyebrow">YIMO Olympiad · Strategy through mathematics</p>
        <h1 id="title">Graphwar<span>.</span></h1>
        <p class="intro">Build a function. Shape the battlefield. Make every curve count.</p>
        <div class="links">
          <a class="button" href="/participant">Competitor portal</a>
          <a class="button secondary" href="/admin">Organizer panel</a>
        </div>
      </div>
      <svg class="graph" viewBox="0 0 420 330" role="img" aria-label="An orange function curve plotted across coordinate axes">
        <defs><pattern id="grid" width="42" height="42" patternUnits="userSpaceOnUse"><path d="M42 0H0V42" fill="none" stroke="#ddd7cd" stroke-width="1"/></pattern></defs>
        <rect width="420" height="330" fill="url(#grid)"/>
        <path d="M24 165H396M210 18V312" stroke="#193c33" stroke-width="2"/>
        <path d="M34 274C98 270 115 238 150 154S208 37 242 112s49 152 144 166" fill="none" stroke="#e78331" stroke-width="6" stroke-linecap="round"/>
        <circle cx="150" cy="154" r="6" fill="#151a17"/><circle cx="242" cy="112" r="6" fill="#151a17"/>
      </svg>
    </section>
    <footer><span>YIMO Graphwar · GPL-3.0</span><a class="source" href="https://github.com/goncalofrankefranco/yimo-graphwar" rel="noopener noreferrer" target="_blank">Explore the full source ↗</a></footer>
  </main>
</body>
</html>`;
