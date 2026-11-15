import { useId } from 'react';

/** Footer landscape with a dedicated mobile crop. */
export function Landscape({ compact = false }: { compact?: boolean }) {
  const id = useId();
  return (
    <svg
      viewBox={compact ? '330 0 1110 640' : '0 0 1440 640'}
      preserveAspectRatio={compact ? 'xMinYMax slice' : 'xMidYMax slice'}
      width="100%"
      height="100%"
      aria-hidden="true"
      style={{ position: 'absolute', inset: 0, display: 'block' }}
    >
      <defs>
        <pattern
          id={`${id}-pz-rock`}
          width="6"
          height="6"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(-48)"
        >
          <line x1="0" y1="0" x2="0" y2="6" stroke="#1B1B1F" strokeWidth="1.5"></line>
        </pattern>
        <pattern
          id={`${id}-pz-mid`}
          width="8"
          height="8"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(-40)"
        >
          <line x1="0" y1="0" x2="0" y2="8" stroke="#3A3A42" strokeWidth="1.1"></line>
        </pattern>
        <pattern
          id={`${id}-pz-far`}
          width="12"
          height="12"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(-48)"
        >
          <line x1="0" y1="0" x2="0" y2="12" stroke="#7A7A82" strokeWidth="1"></line>
        </pattern>
        <pattern
          id={`${id}-pz-orange`}
          width="5"
          height="5"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(-48)"
        >
          <line x1="0" y1="0" x2="0" y2="5" stroke="#E2531A" strokeWidth="2"></line>
        </pattern>
        <pattern
          id={`${id}-pz-roof`}
          width="5"
          height="5"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(38)"
        >
          <line x1="0" y1="0" x2="0" y2="5" stroke="#1B1B1F" strokeWidth="1.6"></line>
        </pattern>
        <pattern
          id={`${id}-pz-green`}
          width="6"
          height="6"
          patternUnits="userSpaceOnUse"
          patternTransform="rotate(30)"
        >
          <line x1="0" y1="0" x2="0" y2="6" stroke="#3E7A2A" strokeWidth="1.6"></line>
        </pattern>
        <pattern id={`${id}-pz-water`} width="18" height="8" patternUnits="userSpaceOnUse">
          <line x1="0" y1="4" x2="11" y2="4" stroke="#4A6B82" strokeWidth="1.1"></line>
        </pattern>
      </defs>
      <g style={{ filter: 'url(#rough)' }} strokeLinejoin="miter" strokeLinecap="square">
        <polygon
          points="0,400 160,360 300,372 460,300 620,340 760,270 900,320 1040,250 1200,300 1320,262 1440,290 1440,502 0,502"
          fill={`url(#${id}-pz-far)`}
          stroke="#7A7A82"
          strokeWidth="1.4"
        ></polygon>
        <polygon
          points="300,330 600,312 900,326 1200,306 1440,318 1440,344 1100,350 700,342 300,352"
          fill="#FBF8EF"
          opacity="0.8"
        ></polygon>
        <polygon
          points="620,502 760,420 900,440 1040,390 1180,430 1300,402 1440,422 1440,502"
          fill={`url(#${id}-pz-mid)`}
          stroke="#3A3A42"
          strokeWidth="1.6"
        ></polygon>
        <polygon
          points="0,432 300,418 640,430 980,414 1300,428 1440,420 1440,446 1100,452 700,444 300,456 0,450"
          fill="#FBF8EF"
          opacity="0.75"
        ></polygon>
        <g>
          <g className="cloud cl-a" style={{ animationDelay: '-8s' }}>
            <path
              d="M880,287 C865,287 865,246 922,248 C916,212 994,201 1009,230 C1030,190 1126,203 1111,241 C1192,238 1198,287 1180,287 Z"
              fill="#FBF8EF"
              opacity="0.92"
            ></path>
            <path
              d="M880,287 C865,287 865,246 922,248 C916,212 994,201 1009,230 C1030,190 1126,203 1111,241 C1192,238 1198,287 1180,287 Z"
              fill={`url(#${id}-pz-far)`}
              stroke="#55555D"
              strokeWidth="1.8"
            ></path>
            <path
              d="M940,269 C958,254 982,254 994,266 M1036,262 C1054,246 1084,248 1090,264"
              fill="none"
              stroke="#55555D"
              strokeWidth="1.5"
            ></path>
          </g>
          <g className="cloud cl-b" style={{ animationDelay: '-20s' }}>
            <path
              d="M470,310 C457,310 457,275 506,276 C501,246 569,236 582,261 C600,227 683,239 670,271 C740,268 746,310 730,310 Z"
              fill="#FBF8EF"
              opacity="0.92"
            ></path>
            <path
              d="M470,310 C457,310 457,275 506,276 C501,246 569,236 582,261 C600,227 683,239 670,271 C740,268 746,310 730,310 Z"
              fill={`url(#${id}-pz-far)`}
              stroke="#55555D"
              strokeWidth="1.8"
            ></path>
            <path
              d="M522,295 C538,282 558,282 569,292 M605,289 C621,275 647,276 652,290"
              fill="none"
              stroke="#55555D"
              strokeWidth="1.5"
            ></path>
          </g>
          <g className="cloud cl-b" style={{ animationDelay: '-4s' }}>
            <path
              d="M1010,401 C999,401 999,373 1041,374 C1036,349 1094,342 1105,362 C1120,335 1190,344 1179,370 C1239,367 1243,401 1230,401 Z"
              fill="#FBF8EF"
              opacity="0.92"
            ></path>
            <path
              d="M1010,401 C999,401 999,373 1041,374 C1036,349 1094,342 1105,362 C1120,335 1190,344 1179,370 C1239,367 1243,401 1230,401 Z"
              fill={`url(#${id}-pz-far)`}
              stroke="#55555D"
              strokeWidth="1.8"
            ></path>
            <path
              d="M1054,389 C1067,379 1085,379 1094,386 M1124,384 C1138,373 1160,374 1164,385"
              fill="none"
              stroke="#55555D"
              strokeWidth="1.5"
            ></path>
          </g>
          <g className="cloud cl-a" style={{ animationDelay: '-14s' }}>
            <path
              d="M640,379 C630,379 630,352 668,353 C664,329 716,322 726,341 C740,315 804,324 794,349 C848,347 852,379 840,379 Z"
              fill="#FBF8EF"
              opacity="0.92"
            ></path>
            <path
              d="M640,379 C630,379 630,352 668,353 C664,329 716,322 726,341 C740,315 804,324 794,349 C848,347 852,379 840,379 Z"
              fill={`url(#${id}-pz-far)`}
              stroke="#55555D"
              strokeWidth="1.8"
            ></path>
            <path
              d="M680,367 C692,357 708,357 716,365 M744,363 C756,352 776,353 780,364"
              fill="none"
              stroke="#55555D"
              strokeWidth="1.5"
            ></path>
          </g>
          <g className="cloud cl-b" style={{ animationDelay: '-26s' }}>
            <path
              d="M300,346 C292,346 292,322 324,323 C320,302 365,296 373,312 C385,289 439,297 431,319 C477,317 480,346 470,346 Z"
              fill="#FBF8EF"
              opacity="0.92"
            ></path>
            <path
              d="M300,346 C292,346 292,322 324,323 C320,302 365,296 373,312 C385,289 439,297 431,319 C477,317 480,346 470,346 Z"
              fill={`url(#${id}-pz-far)`}
              stroke="#55555D"
              strokeWidth="1.8"
            ></path>
            <path
              d="M334,335 C344,327 358,327 365,334 M388,332 C399,322 416,323 419,333"
              fill="none"
              stroke="#55555D"
              strokeWidth="1.5"
            ></path>
          </g>
          <g className="cloud cl-a" style={{ animationDelay: '-2s' }}>
            <path
              d="M560,200 C550,200 550,175 587,176 C583,154 632,148 642,165 C655,141 716,149 706,172 C758,170 761,200 750,200 Z"
              fill="#FBF8EF"
              opacity="0.92"
            ></path>
            <path
              d="M560,200 C550,200 550,175 587,176 C583,154 632,148 642,165 C655,141 716,149 706,172 C758,170 761,200 750,200 Z"
              fill={`url(#${id}-pz-far)`}
              stroke="#8A8A92"
              strokeWidth="1.8"
            ></path>
            <path
              d="M598,189 C609,180 625,180 632,187 M659,185 C670,175 689,176 693,186"
              fill="none"
              stroke="#8A8A92"
              strokeWidth="1.5"
            ></path>
          </g>
        </g>
        <polygon
          points="0,0 130,0 196,92 232,200 262,300 304,390 352,452 404,504 0,504"
          fill={`url(#${id}-pz-rock)`}
          stroke="#1B1B1F"
          strokeWidth="2.2"
        ></polygon>
        <polygon
          points="0,330 100,318 204,380 304,440 384,504 0,504"
          fill={`url(#${id}-pz-green)`}
          stroke="#3E7A2A"
          strokeWidth="1.6"
        ></polygon>
        <polygon
          points="1440,0 1352,0 1302,120 1252,232 1202,332 1148,422 1088,504 1440,504"
          fill={`url(#${id}-pz-rock)`}
          stroke="#1B1B1F"
          strokeWidth="2.2"
        ></polygon>
        <polygon
          points="1440,380 1342,400 1252,460 1204,504 1440,504"
          fill={`url(#${id}-pz-green)`}
          stroke="#3E7A2A"
          strokeWidth="1.6"
        ></polygon>
        <path
          d="M760 236 L772 228 L784 236 M800 258 L810 251 L820 258"
          fill="none"
          stroke="#1B1B1F"
          strokeWidth="2"
        ></path>
        <polygon
          points="400,508 498,508 588,504 588,558 500,566 452,598 400,564"
          fill={`url(#${id}-pz-orange)`}
          opacity="0.38"
        ></polygon>
        <rect x="0" y="504" width="1440" height="136" fill={`url(#${id}-pz-water)`}></rect>
        <path
          d="M700 530 L800 527 M860 552 L1000 548 M640 590 L760 587 M1020 606 L1140 603 M200 556 L300 553 M1080 524 L1150 522"
          fill="none"
          stroke="#4A6B82"
          strokeWidth="1.6"
        ></path>
        <g transform="translate(320,294)">
          <polygon
            points="56,208 92,214 140,212 200,216 268,210 282,220 48,222"
            fill={`url(#${id}-pz-rock)`}
            stroke="#1B1B1F"
            strokeWidth="1.6"
          ></polygon>
          <rect x="190" y="80" width="15" height="28" fill="#1B1B1F"></rect>
          <polygon
            points="172,152 262,147 262,204 172,210"
            fill={`url(#${id}-pz-orange)`}
            stroke="#1B1B1F"
            strokeWidth="2"
          ></polygon>
          <polygon
            points="80,152 125,114 170,152 170,210 80,210"
            fill={`url(#${id}-pz-orange)`}
            stroke="#1B1B1F"
            strokeWidth="2"
          ></polygon>
          <polygon
            points="125,106 224,100 270,148 176,155"
            fill={`url(#${id}-pz-roof)`}
            stroke="#1B1B1F"
            strokeWidth="2"
          ></polygon>
          <polyline
            points="70,156 125,106 180,156"
            fill="none"
            stroke="#1B1B1F"
            strokeWidth="3"
          ></polyline>
          <g fill="#FFFDF6" stroke="#1B1B1F" strokeWidth="1.8">
            <rect x="112" y="128" width="26" height="20"></rect>
            <rect x="92" y="168" width="24" height="20"></rect>
            <rect x="134" y="168" width="24" height="20"></rect>
            <rect x="198" y="164" width="15" height="40"></rect>
            <rect x="232" y="160" width="15" height="20"></rect>
          </g>
          <path
            d="M125 128 L125 148 M112 138 L138 138 M104 168 L104 188 M146 168 L146 188 M239 160 L239 180"
            stroke="#1B1B1F"
            strokeWidth="1.4"
          ></path>
          <polygon
            points="262,198 322,195 328,204 262,208"
            fill={`url(#${id}-pz-far)`}
            stroke="#1B1B1F"
            strokeWidth="1.8"
          ></polygon>
          <path d="M278 208 L278 220 M314 205 L314 218" stroke="#1B1B1F" strokeWidth="2"></path>
        </g>
      </g>
    </svg>
  );
}
